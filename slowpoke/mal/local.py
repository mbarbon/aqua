#!/usr/bin/env python

import datetime
import gzip
import hashlib
import json
import math
import os
import random
import sqlite3
import sys
import xml.etree.ElementTree

from mal.constants import STATUS_COMPLETED, STATUS_DROPPED

EPOCH = datetime.datetime(1970, 1, 1)
STAT_KEYS = ('plantowatch', 'watching', 'completed', 'onhold', 'dropped')

class NoNode(Exception):
    def __init__(self, name):
        self.name = name

def _epoch_days():
    return (datetime.datetime.utcnow() - EPOCH).days

def has_user(basedir, username):
    with _connection(basedir) as conn:
        c = conn.cursor()
        c.execute('SELECT 1 FROM users WHERE username = ?', (username,))
        return bool(c.fetchone())

def store_data(basedir, request_username, data):
    now_days = _epoch_days()
    change_time = 0

    with _connection(basedir) as conn:
        c = conn.cursor()
        tree = xml.etree.ElementTree.parse(data)
        (user_id, username, stats) = _parse_user(tree)
        if not user_id or not username:
            _mark_user_updated(c, request_username)
            return

        anime_list = []
        for anime in tree.getroot():
            if anime.tag == 'myinfo':
                continue

            animedb_id = _int_text(anime, 'series_animedb_id')
            change_time = max(change_time, _int_text(anime, 'my_last_updated'))
            _insert_anime(c, animedb_id, anime)
            anime_list.append(_anime_item(animedb_id, anime, now_days))

        _insert_anime_list(c, user_id, anime_list)
        _insert_user(c, user_id, username, request_username, change_time)
        _insert_user_stats(c, user_id, stats)

def insert_anime_details(basedir, animedb_id, relations, genres, titles, scores):
    with _connection(basedir) as conn:
        c = conn.cursor()

        c.execute("DELETE FROM anime_relations WHERE animedb_id = ?", (animedb_id,))
        c.execute("DELETE FROM anime_genres WHERE animedb_id = ?", (animedb_id,))
        c.execute("DELETE FROM anime_titles WHERE animedb_id = ?", (animedb_id,))
        c.execute("DELETE FROM anime_details WHERE animedb_id = ?", (animedb_id,))

        for related, relation in relations.items():
            c.execute("INSERT OR REPLACE INTO anime_relations VALUES (?, ?, ?)", (
                animedb_id,
                related,
                relation))

        for index, (genre_id, genre_name) in enumerate(genres):
            c.execute("INSERT OR REPLACE INTO anime_genres VALUES (?, ?, ?)", (
                animedb_id,
                genre_id,
                index))
            c.execute("INSERT OR REPLACE INTO anime_genre_names VALUES (?, ?)", (
                genre_id,
                genre_name))

        for title in titles:
            c.execute("INSERT OR REPLACE INTO anime_titles VALUES (?, ?)", (
                animedb_id,
                title))

        c.execute("INSERT OR REPLACE INTO anime_details VALUES (?, ?, ?, ?)", (animedb_id, scores.get('rank', 0), scores.get('popularity', 0), scores.get('score', 0)))
        c.execute("INSERT OR REPLACE INTO anime_details_update VALUES (?, strftime('%s', 'now'))", (animedb_id,))

def create_tables(basedir):
    with _connection(basedir) as conn:
        c = conn.cursor()
        c.execute("CREATE TABLE IF NOT EXISTS users (user_id INTEGER PRIMARY KEY, username VARCHAR(20) NOT NULL, last_update INTEGER NOT NULL, last_change INTEGER NOT NULL)")
        c.execute("CREATE INDEX IF NOT EXISTS username_index ON users (username)")
        c.execute("CREATE TABLE IF NOT EXISTS user_anime_stats (user_id INTEGER PRIMARY KEY, planned INTEGER NOT NULL, watching INTEGER NOT NULL, completed INTEGER NOT NULL, onhold INTEGER NOT NULL, dropped INTEGER NOT NULL)")

        c.execute("CREATE TABLE IF NOT EXISTS anime (animedb_id INTEGER PRIMARY KEY, title VARCHAR(255) NOT NULL, type INTEGER NOT NULL, episodes INTEGER NOT NULL, status INTEGER NOT NULL, start INTEGER, end INTEGER, image VARCHAR(255) NOT NULL)")
        c.execute("CREATE TABLE IF NOT EXISTS anime_details (animedb_id INTEGER PRIMARY KEY, rank INTEGER NOT NULL, popularity INTEGER NOT NULL, score INTEGER NOT NULL)")
        c.execute("CREATE TABLE IF NOT EXISTS anime_relations (animedb_id INTEGER NOT NULL, related_id INTEGER NOT NULL, relation INTEGER NOT NULL)")
        c.execute("CREATE UNIQUE INDEX IF NOT EXISTS anime_relations_index ON anime_relations (animedb_id, related_id)")
        c.execute("CREATE TABLE IF NOT EXISTS anime_genres (animedb_id INTEGER NOT NULL, genre_id INTEGER NOT NULL, sort_order INTEGER NOT NULL)")
        c.execute("CREATE TABLE IF NOT EXISTS anime_titles (animedb_id INTEGER NOT NULL, title VARCHAR(255) NOT NULL)")
        c.execute("CREATE UNIQUE INDEX IF NOT EXISTS anime_genres_index ON anime_genres (animedb_id, genre_id)")
        c.execute("CREATE TABLE IF NOT EXISTS anime_details_update (animedb_id INTEGER PRIMARY KEY, last_update INTEGER NOT NULL)")

        c.execute("CREATE TABLE IF NOT EXISTS anime_list (user_id INTEGER NOT NULL PRIMARY KEY, anime_list BLOB NOT NULL)")

        c.execute("CREATE TABLE IF NOT EXISTS relation_names (relation INTEGER PRIMARY KEY, description VARCHAR(30))")
        c.execute("INSERT OR REPLACE INTO relation_names VALUES (1, 'side_story')")
        c.execute("INSERT OR REPLACE INTO relation_names VALUES (2, 'alternative_version')")
        c.execute("INSERT OR REPLACE INTO relation_names VALUES (3, 'sequel')")
        c.execute("INSERT OR REPLACE INTO relation_names VALUES (4, 'other')")
        c.execute("INSERT OR REPLACE INTO relation_names VALUES (5, 'prequel')")
        c.execute("INSERT OR REPLACE INTO relation_names VALUES (6, 'parent_story')")
        c.execute("INSERT OR REPLACE INTO relation_names VALUES (7, 'full_story')")

        c.execute("CREATE TABLE IF NOT EXISTS anime_genre_names (genre INTEGER PRIMARY KEY, description VARCHAR(30))")

def users_needing_update(basedir):
    old_inactive_budget = 0.1
    users_max_age = 10
    users_bucket_start = 2
    users_bucket_exponent = 1.1
    users_bucket_budget = 10
    users = []

    def concat_users(users, cursor):
        return users + [user for (user,) in c.fetchall()]

    with _connection(basedir) as conn:
        c = conn.cursor()

        c.execute("SELECT (strftime('%s', 'now') - MIN(last_change)) / 86400 FROM users WHERE last_change > 1234567890")
        min_inactive_user_change, = c.fetchone()

        # users that were inactive last time we checked
        bucket_size = users_bucket_start
        bucket_start = users_max_age
        while bucket_start < min_inactive_user_change:
            c.execute("SELECT username FROM users WHERE last_change >= strftime('%s', 'now') - 86400 * ? AND last_change < strftime('%s', 'now') - 86400 * ? AND last_update < strftime('%s', 'now') - 86400 * ? AND username <> '' ORDER BY last_update ASC LIMIT ?", (bucket_start + bucket_size, bucket_start, users_bucket_start, users_bucket_budget))
            users = concat_users(users, c)
            bucket_start += bucket_size
            bucket_size *= users_bucket_exponent

        # very old users or ones that failed to fetch
        c.execute("SELECT username FROM users WHERE last_change < 1234567890 AND username <> '' ORDER BY last_update ASC LIMIT ?", (math.floor(len(users) * old_inactive_budget),))
        users = concat_users(users, c)

    return users

def _mark_user_updated(c, username):
    c.execute("UPDATE users SET last_update = strftime('%s', 'now'), last_change = 1 WHERE username = ?", (username,))

def _insert_user(c, user_id, username, request_username, change_time):
    if change_time:
        c.execute("INSERT OR REPLACE INTO users VALUES (?, ?, strftime('%s', 'now'), ?)", (user_id, username, change_time))
    else:
        c.execute("INSERT OR REPLACE INTO users VALUES (?, ?, strftime('%s', 'now'), (SELECT last_change FROM users WHERE user_id = ?))", (user_id, username, user_id))

    # here we set username to '' because it's hard to make models cope with
    # users being deleted

    # It looks like that when usernames change case, a new user id is created,
    # or something like that, so when the requested and received usernames
    if username != request_username:
        c.execute("UPDATE users SET username = '' WHERE username = ?", (request_username,))
    # This is to clear bed data caused by me not knowing about the above
    c.execute("UPDATE users SET username = '' WHERE username = ? AND user_id <> ?", (username, user_id))

def _insert_user_stats(c, user_id, stats):
    values = tuple(stats[k] for k in STAT_KEYS)
    c.execute("INSERT OR REPLACE INTO user_anime_stats VALUES (?, ?, ?, ?, ?, ?)", (user_id,) + values)

def _insert_anime(c, animedb_id, anime):
    c.execute("INSERT OR REPLACE INTO anime VALUES (?, ?, ?, ?, ?, strftime('%s', ?), strftime('%s', ?), ?)", (
                  animedb_id,
                  _text(anime, 'series_title'),
                  _int_text(anime, 'series_type'),
                  _int_text(anime, 'series_episodes'),
                  _int_text(anime, 'series_status'),
                  _text(anime, 'series_start'),
                  _text(anime, 'series_end'),
                  _text(anime, 'series_image')))

def _anime_item(animedb_id, anime, now_days):
    return [animedb_id,
            _int_text(anime, 'my_status'),
            _int_text(anime, 'my_score'),
            now_days]

def _load_blob(blob):
    if sys.version_info >= (3, 6):
        return json.loads(gzip.decompress(blob))
    else:
        return json.loads(gzip.decompress(blob).decode())

def _insert_anime_list(c, user_id, data):
    c.execute("SELECT anime_list FROM anime_list WHERE user_id = ?", (user_id,))
    row = c.fetchone()
    if row:
        previous_map = {item[0]: item for item in _load_blob(row[0])}

        changed = False
        for item in data:
            previous = previous_map.get(item[0])
            if not previous:
                changed = True
            elif previous[1] != item[1] or previous[2] != item[2]:
                changed = True

            if item[1] == STATUS_COMPLETED or item[1] == STATUS_DROPPED:
                if previous and previous[1] == item[1]:
                    item[3] = previous[3] if len(previous) > 3 else 0
            else:
                item[3] = 0
    else:
        changed = True

    if not changed:
        return

    json_blob = json.dumps(data).encode()
    compressed_blob = gzip.compress(json_blob, compresslevel=6)
    c.execute('INSERT OR REPLACE INTO anime_list VALUES (?, ?)', (user_id, compressed_blob))

def anime_needing_update(basedir):
    with _connection(basedir) as conn:
        c = conn.cursor()
        c.execute("SELECT a.animedb_id, a.title FROM anime AS a LEFT JOIN anime_details_update au ON a.animedb_id = au.animedb_id AND last_update > strftime('%s', 'now') - ? WHERE last_update IS NULL", (86400 * 20,))
        return c.fetchall()

def _parse_user(tree):
    myinfo = tree.getroot().find('myinfo')
    if myinfo is None:
        return (None, None, None)
    user_name = _text(myinfo, 'user_name')
    user_id = _text(myinfo, 'user_id')
    stats = {}
    if user_id is None:
        raise Exception('Malformed user data: missing user_id: %s' %path)
    if user_name is None:
        raise Exception('Malformed user data: missing user_name: %s' %path)
    for stat in STAT_KEYS:
        count_text = _text(myinfo, 'user_' + stat)
        if count_text is None:
            stats[stat] = 0
        else:
            stats[stat] = int(count_text)
    return (user_id, user_name, stats)

def _text(node, name):
    child = node.find(name)
    if child is None:
        raise NoNode(name)
    if not child.text:
        raise NoNode(name)
    return child.text

def _int_text(node, name):
    return int(_text(node, name))

def _connection(basedir):
    return sqlite3.connect(os.path.join(basedir, 'maldump.sqlite'))
