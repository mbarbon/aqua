// @flow
import React, { Component, PureComponent } from 'react'
import { AnimeList, LocalAnimeList } from './components/AnimeList'
import {
  localAnimeList,
  localState,
  filteredRecommendations,
  aquaAutocomplete
} from '../shared/state/Globals'
import Header from './Header'
import AquaButton from './components/AquaButton'

import type { Anime, Recommendations } from '../shared/backend/types'
import type { LocalAnime } from '../shared/state/types'

class AnimeFilterButton {
  tag: string
  description: string
  show: boolean

  constructor (tag: string, description: string, show: boolean) {
    this.tag = tag
    this.description = description
    this.show = show
  }

  toggleTag () {
    return new AnimeFilterButton(this.tag, this.description, !this.show)
  }

  statusDescription () {
    return `${this.show ? 'Hide' : 'Show'} "${this.description}"`
  }
}

type Props = {
  recommendations: ?Recommendations,
  userMode: string,
  malUsername?: ?string,
  queuePosition: ?number,
  canRefresh: boolean,
  autocompleteAnime: ?Array<Anime>,
  localAnimeList?: ?Array<LocalAnime>
}

type State = {
  showAiring: boolean,
  showHideTags: Array<AnimeFilterButton>,
  manualEditMode: boolean
}

export default class UserRecommendations extends Component<Props, State> {
  constructor (props: Props) {
    super(props)
    this.state = {
      showAiring: false,
      showHideTags: [
        new AnimeFilterButton('planned', 'plan to watch', true),
        new AnimeFilterButton('franchise', 'related', false),
        new AnimeFilterButton('planned-franchise', 'related is planned', false)
      ],
      manualEditMode: false
    }
    this.updateFilters()
  }

  toggleShowAiring () {
    this.setState(previous => {
      return {
        showAiring: !previous.showAiring
      }
    })
  }

  toggleEditLocal () {
    this.setState(previous => {
      return {
        manualEditMode: !previous.manualEditMode
      }
    })
  }

  toggleStatusFilter (index: number) {
    this.state.showHideTags[index] = this.state.showHideTags[index].toggleTag()
    this.setState({ showHideTags: this.state.showHideTags })
    this.updateFilters()
  }

  updateFilters () {
    filteredRecommendations.setStatusFilter(
      this.state.showHideTags.filter(sh => sh.show).map(sh => sh.tag)
    )
  }

  renderStatusFilters () {
    let result = []
    for (let i = 0; i < this.state.showHideTags.length; ++i) {
      let showHide = this.state.showHideTags[i]
      result.push(
        <AquaButton
          key={'showHide.' + i}
          onClick={this.toggleStatusFilter.bind(this, i)}
          label={showHide.statusDescription()}
        />
      )
    }
    return result
  }

  performSearch (e: SyntheticInputEvent<HTMLInputElement>) {
    aquaAutocomplete.setTerm(e.target.value)
  }

  changeLocalRating (anime: Anime, rating: number) {
    localAnimeList.addRating(anime, rating)
  }

  removeLocalRating (anime: Anime) {
    localAnimeList.removeRating(anime)
  }

  render () {
    const isLocal = this.props.userMode === 'local'
    const isMal = this.props.userMode === 'mal'
    const hasLocalList =
      isLocal && this.props.localAnimeList && !!this.props.localAnimeList.length

    return (
      <div>
        <Header
          userMode={this.props.userMode}
          malUsername={this.props.malUsername}
          queuePosition={this.props.queuePosition}
          canRefresh={this.props.canRefresh}
        />
        {isLocal && (
          <div
            className='recommendation-source aqua-body'
            id='recommendation-source'
          >
            Add watched anime{' '}
            <input
              className='text-input'
              id='searchAnime'
              type='text'
              onChange={this.performSearch.bind(this)}
            />
          </div>
        )}
        <div className='recommendation-filter' id='recommendation-filter'>
          {isMal && this.renderStatusFilters()}
          {isLocal &&
            hasLocalList && (
              <AquaButton
                onClick={this.toggleEditLocal.bind(this)}
                label={
                  this.state.manualEditMode
                    ? 'See recommendations'
                    : 'Modify your anime list'
                }
              />
            )}
          {this.props.recommendations && (
            <AquaButton
              onClick={this.toggleShowAiring.bind(this)}
              label={
                this.state.showAiring
                  ? 'Hide airing anime'
                  : 'Show airing anime'
              }
            />
          )}
        </div>
        {this.props.autocompleteAnime && (
          <div id='recommendations' className='aqua-body'>
            <AnimeList
              anime={this.props.autocompleteAnime}
              onRatingChange={this.changeLocalRating.bind(this)}
            />
          </div>
        )}
        {this.state.manualEditMode &&
          !this.props.autocompleteAnime &&
          this.props.localAnimeList != null && (
            <div id='local-list-edit' className='aqua-body'>
              <h3>My anime list</h3>
              <LocalAnimeList
                anime={this.props.localAnimeList}
                onRatingChange={this.changeLocalRating.bind(this)}
                onRatingRemove={this.removeLocalRating.bind(this)}
              />
            </div>
          )}
        {this.props.recommendations &&
          !this.state.manualEditMode &&
          !this.props.autcompleteAnime && (
            <div id='recommendations' className='aqua-body'>
              {this.state.showAiring &&
                this.props.recommendations.airing.length && (
                  <h3 className='recommendation-description'>Airing anime</h3>
                )}
              {this.state.showAiring &&
                this.props.recommendations.airing.length && (
                  <AnimeList anime={this.props.recommendations.airing} />
                )}
              <h3 className='recommendation-description'>Completed anime</h3>
              <AnimeList
                anime={this.props.recommendations.completed}
                onRatingChange={
                  isLocal ? this.changeLocalRating.bind(this) : null
                }
              />
            </div>
          )}
      </div>
    )
  }
}
