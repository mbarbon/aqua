#!/usr/bin/env python

import bs4
import gzip
import logging
import re
import urllib.parse
import urllib.request

BASE = 'http://myanimelist.net/'
LOGGER = logging.getLogger('mal.web')

def fetch_user_info(username):
    urlargs = urllib.parse.urlencode({'u': username,
                                          'status': 'all',
                                          'type': 'anime'})

    url = BASE + '/malappinfo.php?' + urlargs
    try:
        with _request(url, timeout=3) as response:
            if response.status != 200:
                LOGGER.warn('Error while fetching user info: %s', response.reason)
            else:
                return re.sub(b'[\x00-\x1f]', b' ', _body(response))
    except Exception as e:
        LOGGER.warn('Exception while fetching user info: %s', e)

    return None

def fetch_random_users():
    url = BASE + '/users.php'
    try:
        with _request(url, timeout=3) as response:
            if response.status != 200:
                LOGGER.warn('Error while fetching users: %s', response.reason)
            else:
                return parse_users(_decoded_body(response))
    except Exception as e:
        LOGGER.warn('Exception while fetching users: %s', e)

    return None

def fetch_anime_page(anime_id):
    url = BASE + '/anime/' + str(anime_id)
    try:
        with _request(url, timeout=3) as response:
            if response.status != 200:
                LOGGER.warn('Error while fetching anime: %s', response.reason)
            else:
                return parse_anime_page(anime_id, _decoded_body(response))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return ({}, [], set(), {})
        else:
            LOGGER.warn('HTTP error while fetching anime: %s', e)
    except Exception as e:
        LOGGER.warn('Exception while fetching anime: %s', e)

    return None, None, None, None

def _request(url, **args):
    request =  urllib.request.Request(url)
    request.add_header('Accept-Encoding', 'gzip')

    return urllib.request.urlopen(request, **args)

def _body(response):
    compression = response.getheader('Content-Encoding')

    body = response.read()
    if compression == 'gzip':
        body = gzip.decompress(body)
    elif compression:
        raise Exception("Unrecognized Content-Encoding: " + compression)

    return body

def _decoded_body(response):
    return _body(response).decode('utf-8', 'replace')

def parse_users(body):
    return set(match.group(1)
                for match in
                   re.finditer(r'<a\s[^>]*href="/profile/([^"]+)"', body))

RELATIONS = {
    'Side story': 1,
    'Alternative version': 2,
    'Sequel': 3,
    # way too random (e.g. might include commercials)
    # 'Other': 4,
    'Prequel': 5,
    'Parent story': 6,
    'Full story': 7,
}

def parse_anime_page(anime_id, body):
    soup = bs4.BeautifulSoup(body, 'html.parser')
    relations = {}
    for table in soup.find_all('table'):
        if 'anime_detail_related_anime' not in table.get('class', []):
            continue

        relation = None
        for td in table.find_all('td'):
            text = td.text
            if text.endswith(':') and text[:-1] in RELATIONS:
                relation = RELATIONS[text[:-1]]
            elif relation:
                for link in td.find_all('a'):
                    if not link.get('href'):
                        continue
                    href = link.get('href')
                    match = re.match(r'/anime/(\d+)/(.*)', href)
                    if not match:
                        continue
                    relations[int(match.group(1))] = relation
                relation = None

    genres = []
    for span in soup.find_all('span'):
        if span.text.strip() != "Genres:":
            continue

        for link in span.parent.find_all('a'):
            if not link.get('href'):
                continue
            href = link.get('href')
            match = re.match(r'/anime/genre/(\d+)/(.*)', href)
            if not match:
                continue
            genres.append((int(match.group(1)), link.text))

        break

    titles = set()
    for h2 in soup.find_all('h2'):
        if h2.text.strip() != 'Alternative Titles':
            continue

        sibling = h2.next_sibling
        while not hasattr(sibling, 'name') or sibling.name != 'h2':
            if hasattr(sibling, 'name') and sibling.name == 'div':
                span = sibling.find('span')
                if span and span.text.strip() in ['English:', 'Synonyms:']:
                    content = span.next_sibling.string.strip()
                    titles = titles | set(part.strip() for part in content.split(','))

            sibling = sibling.next_sibling

        break

    scores = {'score': 0, 'rank': 0, 'popularity': 0}
    stats = soup.find('div', {'class': 'stats-block'})
    if stats:
        score = stats.find('div', {'class': 'score'})
        rank = stats.find('span', {'class': 'ranked'})
        popularity = stats.find('span', {'class': 'popularity'})
        if score:
            score_string = score.text.strip()
            if score_string != 'N/A':
                scores['score'] = int(100 * float(score_string))
        if rank:
            match = re.match(r'.*#(\d+)', rank.text)
            if match:
                scores['rank'] = int(match.group(1))
        if popularity:
            match = re.match(r'.*#(\d+)', popularity.text)
            if match:
                scores['popularity'] = int(match.group(1))

    return relations, genres, titles, scores
