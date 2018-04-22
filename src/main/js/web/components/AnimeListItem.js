// @flow
import React, { PureComponent } from 'react'
import StarRating from 'react-star-rating-component'
import { malLink, malLinkClick } from '../../shared/helpers/MAL'
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

type AnimeListItem_onRemove = Anime => void
type AnimeListItem_onChange = (anime: Anime, rating: number) => void
type AnimeListItem_onClick = Anime => void
type AnimeListItem_makeLink = Anime => string

type Props = {
  anime: Anime,
  onChange?: ?AnimeListItem_onChange,
  onRemove?: ?AnimeListItem_onRemove,
  onClick?: ?AnimeListItem_onClick,
  makeLink?: ?AnimeListItem_makeLink
}

class AnimeListItem extends PureComponent<Props> {
  render () {
    let { anime, onChange, onRemove } = this.props
    let onClick = this.props.onClick || malLinkClick
    let makeLink = this.props.makeLink || malLink
    let title = anime.displayTitle || anime.title

    return (
      <div className='recommendation-item'>
        <a
          title={title}
          onClick={onClick.bind(anime)}
          href={makeLink(anime)}
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
            alt={title}
          />
        </a>
        <div className='recommendation-details'>
          <div className='header'>
            <a
              onClick={onClick.bind(anime)}
              href={makeLink(anime)}
              title={title}
              target='_blank'
            >
              {title}
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
                onChange && onChange(this.props.anime, value * 2)
              }
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
export type {
  AnimeListItem_onRemove,
  AnimeListItem_onChange,
  AnimeListItem_onClick,
  AnimeListItem_makeLink
}
