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
            _insert_anime(c, animedb_id, anime)
            anime_list.append(_anime_item(animedb_id, anime, now_days))

        changed = _insert_anime_list(c, user_id, anime_list)
        _insert_user(c, user_id, username, changed)
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
    with _connection(basedir) as conn:
        c = conn.cursor()
        c.execute("SELECT username FROM users WHERE (last_update < strftime('%s', 'now') - 86400 * 15 AND (last_change = 0 OR last_change > strftime('%s', 'now') - 86400 * 30)) OR last_update < strftime('%s', 'now') - 86400 * 60")
        return [user for (user,) in c.fetchall()]

def _mark_user_updated(c, username):
    c.execute("UPDATE users SET last_update = strftime('%s', 'now'), last_change = 1 WHERE username = ?", (username,))

def _insert_user(c, user_id, username, changed):
    if changed:
        c.execute("INSERT OR REPLACE INTO users VALUES (?, ?, strftime('%s', 'now'), strftime('%s', 'now'))", (user_id, username))
    else:
        c.execute("INSERT OR REPLACE INTO users VALUES (?, ?, strftime('%s', 'now'), (SELECT last_change FROM users WHERE user_id = ?))", (user_id, username, user_id))

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

    return changed

def fetch_anime_list(basedir, username):
    with _connection(basedir) as conn:
        c = conn.cursor()
        c.execute('SELECT user_id FROM users WHERE username = ?', (username,))
        (user_id,) = c.fetchone()
        c.execute('SELECT anime_list FROM anime_list WHERE user_id = ?', (user_id,))
        (blob,) = c.fetchone()
        return _load_blob(blob)

def count_to_bucket(count):
    return int(4 * (math.log(count * 10) - 2))

def bucket_to_count(bucket):
    return math.floor(math.exp((bucket / 4.) + 2) / 10. + 0.5)

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
