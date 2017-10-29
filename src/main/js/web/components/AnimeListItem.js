// @flow
import React, { PureComponent } from 'react'
import StarRating from 'react-star-rating-component'
import './AnimeListItem.css'

import type { Anime } from '../../shared/backend/types'
import type { LocalAnime } from '../../shared/state/types'

const coverWidth = 84
const coverHeight = 114

const tagDescriptions = {
  planned: 'Plan to watch',
  franchise: 'Related to watched anime',
  'planned-franchise': 'Related to planned anime'
}

function malLink (anime) {
  return `https://myanimelist.net/anime/${anime.animedbId}`
}

function malLinkClick (anime) {
  if (window.ga) {
    window.ga('send', 'event', {
      eventCategory: 'mal_link',
      eventAction: 'click',
      eventLabel: '' + anime.animedbId
    })
  }
}

type AnimeListItem_onRemove = Anime => void
type AnimeListItem_onChange = (anime: Anime, rating: number) => void

type Props = {
  anime: Anime,
  onChange?: ?AnimeListItem_onChange,
  onRemove?: ?AnimeListItem_onRemove
}

class AnimeListItem extends PureComponent<Props> {
  render () {
    let { anime, onChange, onRemove } = this.props

    return (
      <div className='recommendation-item'>
        <a
          title={anime.title}
          onClick={malLinkClick.bind(anime)}
          href={malLink(anime)}
          target='_blank'
        >
          <img
            style={{
              maxWidth: coverWidth,
              maxHeight: coverHeight,
              height: 'auto'
            }}
            className='recommendation-image'
            src={anime.image}
            alt={anime.title}
          />
        </a>
        <div className='recommendation-details'>
          <div className='header'>
            <a
              onClick={malLinkClick.bind(anime)}
              href={malLink(anime)}
              title={anime.title}
              target='_blank'
            >
              {anime.title}
            </a>
          </div>
          <div className='details' title={anime.genres}>
            {anime.genres}
          </div>
          <div className='details'>
            {anime.episodes} eps
            {!!anime.franchiseEpisodes &&
              ` (${anime.franchiseEpisodes} in franchise)`}
          </div>
          <div className='details'>{anime.season}</div>
          {anime.tags && (
            <div className='details'>
              {tagDescriptions[anime.tags] || anime.tags}
            </div>
          )}
          {this.props.onChange && 'Your rating: '}
          {onChange && (
            <StarRating
              name={'anime.rating.' + anime.animedbId}
              emptyStarColor='#aaa'
              starColor='#fcd382'
              value={this._rating()}
              totalStars={5}
              onStarClick={value =>
                onChange && onChange(this.props.anime, value * 2)}
            />
          )}
          {onRemove && (
            <input
              type='button'
              value='Remove'
              onClick={() => onRemove && onRemove(this.props.anime)}
            />
          )}
        </div>
      </div>
    )
  }

  _rating () {
    let anime: any = this.props.anime
    return (anime.userRating || 0) / 2
  }
}

export { AnimeListItem }
export type { AnimeListItem_onRemove, AnimeListItem_onChange }
