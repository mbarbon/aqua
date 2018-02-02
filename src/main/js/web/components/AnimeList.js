// @flow
import React, { PureComponent } from 'react'
import { AnimeListItem } from './AnimeListItem'

import type { Anime } from '../../shared/backend/types'
import type { LocalAnime } from '../../shared/state/types'
import type {
  AnimeListItem_onRemove,
  AnimeListItem_onChange,
  AnimeListItem_onClick,
  AnimeListItem_makeLink
} from './AnimeListItem'

class AnimeList extends PureComponent<{
  anime: Array<Anime>,
  onRatingChange?: ?AnimeListItem_onChange,
  onAnimeClick?: ?AnimeListItem_onClick,
  makeAnimeLink?: ?AnimeListItem_makeLink
}> {
  render () {
    return (
      <div className='recommendation-list'>
        {this.props.anime.map(anime => (
          <AnimeListItem
            anime={anime}
            key={'anime.' + anime.animedbId}
            onChange={this.props.onRatingChange}
            onClick={this.props.onAnimeClick}
            makeLink={this.props.makeAnimeLink}
          />
        ))}
      </div>
    )
  }
}

class LocalAnimeList extends PureComponent<{
  anime: Array<LocalAnime>,
  onRatingChange?: ?AnimeListItem_onChange,
  onRatingRemove?: ?AnimeListItem_onRemove,
  onAnimeClick?: ?AnimeListItem_onClick,
  makeAnimeLink?: ?AnimeListItem_makeLink
}> {
  render () {
    return (
      <div className='recommendation-list'>
        {this.props.anime.map(anime => (
          <AnimeListItem
            anime={anime}
            key={'anime.' + anime.animedbId}
            onChange={this.props.onRatingChange}
            onRemove={this.props.onRatingRemove}
            onClick={this.props.onAnimeClick}
            makeLink={this.props.makeAnimeLink}
          />
        ))}
      </div>
    )
  }
}

export { AnimeList, LocalAnimeList }
