#!/usr/bin/env python

import io
import logging
import mal.local
import mal.web
import sys
import threading
import time

LOGGER = logging.getLogger('slowpoke')
BASEDIR = 'maldump'

def fetch_new_users():
    last_users_fetch = time.time()

    while True:
        try:
            LOGGER.info('Fetching new user sample')
            users = [user
                        for user in (mal.web.fetch_random_users() or [])
                         if not mal.local.has_user(BASEDIR, user)]
            if not users:
                LOGGER.info('No new users')
                last_users_fetch = wait_until(last_users_fetch, 30)
                continue

            for username in users:
                time.sleep(1)
                LOGGER.info('Fetching user data for %s', username)
                userdata = mal.web.fetch_user_info(username)
                if userdata:
                    mal.local.store_data(BASEDIR, username, io.BytesIO(userdata))
        except:
            LOGGER.warn("Unexpected error", exc_info=sys.exc_info())
        finally:
            last_users_fetch = wait_until(last_users_fetch, 30)

def refresh_users():
    last_users_fetch = time.time()

    while True:
        try:
            LOGGER.info('Fetching users needing refresh')
            users = mal.local.users_needing_update(BASEDIR)
            if not users:
                LOGGER.info('No users to refresh')
                last_users_fetch = wait_until(last_users_fetch, 120)
                continue

            for username in users:
                time.sleep(2)
                LOGGER.info('Refreshing user data for %s', username)
                userdata = mal.web.fetch_user_info(username)
                if userdata:
                    mal.local.store_data(BASEDIR, username, io.BytesIO(userdata))
        except:
            LOGGER.warn("Unexpected error", exc_info=sys.exc_info())
        finally:
            last_users_fetch = wait_until(last_users_fetch, 120)

def fetch_anime_details():
    last_anime_fetch = time.time()

    while True:
        try:
            missing = mal.local.anime_needing_update(BASEDIR)
            if not missing:
                last_anime_fetch = wait_until(last_anime_fetch, 120)
                continue

            for anime_id, anime_title in missing:
                LOGGER.info('Fetching new anime details for %s', anime_title)

                relations, genres, titles, scores = mal.web.fetch_anime_page(anime_id)
                if relations is None:
                    continue
                mal.local.insert_anime_details(BASEDIR, anime_id, relations, genres, titles - set([anime_title]), scores)

                last_anime_fetch = wait_until(last_anime_fetch, 10)
        except:
            LOGGER.warn("Unexpected error", exc_info=sys.exc_info())
        finally:
            last_anime_fetch = wait_until(last_anime_fetch, 30)

def main():
    mal.local.create_tables(BASEDIR)
    threading.Thread(target=fetch_new_users).start()
    threading.Thread(target=refresh_users).start()
    threading.Thread(target=fetch_anime_details).start()

def wait_until(last_time, until_time):
    now = time.time()
    if now < last_time + until_time:
        time.sleep(last_time + until_time - now)
    return time.time()

if __name__ == '__main__':
    logging.basicConfig(
        level='INFO',
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    )
    main()
