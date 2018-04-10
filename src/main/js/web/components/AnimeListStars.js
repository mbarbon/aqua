// @flow
import React, { PureComponent } from 'react'
import StarRating from 'react-star-rating-component'

import type { Anime } from '../../shared/backend/types'

type AnimeListStars_onChange = (anime: Anime, rating: number) => void

type Props = {
  anime: Anime,
  onChange?: ?AnimeListStars_onChange
}

class AnimeListStars extends PureComponent<Props> {
  render () {
    let { onChange } = this.props

    return (
      <span>
        Your rating:
        <StarRating
          name={'anime.rating.' + this.props.anime.animedbId}
          emptyStarColor='#aaa'
          starColor='#fcd382'
          value={this._rating()}
          totalStars={5}
          onStarClick={value =>
            onChange && onChange(this.props.anime, value * 2)
          }
        />
      </span>
    )
  }

  _rating () {
    let anime: any = this.props.anime
    return (anime.userRating || 0) / 2
  }
}

export default AnimeListStars
