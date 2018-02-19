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

type AutocompleteAnimeListProps = {
  anime: Array<Anime>,
  localAnime?: ?Array<LocalAnime>,
  onRatingChange: AnimeListItem_onChange
}

class AutocompleteAnimeList extends PureComponent<
  AutocompleteAnimeListProps,
  { mergedAnime: Array<LocalAnime> }
> {
  constructor (props: AutocompleteAnimeListProps) {
    super(props)

    this.state = {
      mergedAnime: this.mergeLists(this.props.localAnime)
    }
  }

  componentWillReceiveProps (nextProps: AutocompleteAnimeListProps) {
    this.setState({
      mergedAnime: this.mergeLists(this.props.localAnime)
    })
  }

  mergeLists (nextLocalAnime: ?Array<LocalAnime>): Array<LocalAnime> {
    if (nextLocalAnime && nextLocalAnime.length > 0) {
      let ratingMap = {}
      let mergedAnime = []

      for (let localAnime of nextLocalAnime) {
        ratingMap[localAnime.animedbId] = localAnime.userRating
      }
      for (let anime of this.props.anime) {
        let mergedItem = {
          ...anime,
          userRating: ratingMap[anime.animedbId],
          userStatus: 2
        }

        mergedAnime.push(mergedItem)
      }

      return mergedAnime
    } else {
      return []
    }
  }

  render () {
    if (this.state.mergedAnime.length == 0) {
      return (
        <AnimeList
          anime={this.props.anime}
          onRatingChange={this.props.onRatingChange}
        />
      )
    } else {
      return (
        <LocalAnimeList
          anime={this.state.mergedAnime}
          onRatingChange={this.props.onRatingChange}
        />
      )
    }
  }
}

export { AnimeList, AutocompleteAnimeList, LocalAnimeList }
