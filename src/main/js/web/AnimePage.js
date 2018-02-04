// @flow
import React, { PureComponent } from 'react'
import { NoUserHeader } from './Header'
import Spinner from './components/Spinner'
import { AnimeListItem } from './components/AnimeListItem'
import { AnimeList } from './components/AnimeList'
import { aquaSingleAnimeRecommednations } from '../shared/state/Globals'
import { malLink, malLinkClick } from '../shared/helpers/MAL'

import type { Anime, Recommendations } from '../shared/backend/types'

const coverWidth = 168
const coverHeight = 228

function ignoreClick (anime) {}
function makeAnimeLink (anime) {
  return `/anime/details/${anime.animedbId}`
}

type Props = {
  match: { params: { animedbId: string } }
}

type State = {
  animedbId: number,
  anime?: Anime,
  recommendations?: Recommendations
}

class AnimeDetails extends PureComponent<{ anime: Anime }> {
  render () {
    let anime = this.props.anime

    return (
      <div className='aqua-body'>
        <h1>{anime.title}</h1>
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
            className='anime-image'
            src={anime.image}
            alt={anime.title}
          />
        </a>
        <div className='anime-details'>
          <div className='details' title={anime.genres}>
            {anime.genres}
          </div>
          <div className='details'>
            {anime.episodes} eps
            {!!anime.franchiseEpisodes &&
              ` (${anime.franchiseEpisodes} in franchise)`}
          </div>
          <div className='details'>{anime.season}</div>
        </div>
        <div style={{ clear: 'both' }} />
      </div>
    )
  }
}

class AnimeRecommendations extends PureComponent<{
  recommendations: Recommendations
}> {
  render () {
    return (
      <div className='aqua-body'>
        {this.props.recommendations.airing.length > 0 && (
          <h3 className='recommendation-description'>
            Airing anime people are watching
          </h3>
        )}
        <AnimeList anime={this.props.recommendations.airing} />
        {this.props.recommendations.airing.length > 0 && (
          <h3 className='recommendation-description'>People also liked</h3>
        )}
        <AnimeList
          anime={this.props.recommendations.completed}
          onAnimeClick={ignoreClick}
          makeAnimeLink={makeAnimeLink}
        />
      </div>
    )
  }
}

class AnimePage extends PureComponent<Props, State> {
  constructor (props: Props) {
    super(props)

    this.state = {
      animedbId: parseInt(props.match.params.animedbId, 10)
    }

    if (isNaN(this.state.animedbId)) {
      throw 'Invalid anime id'
    }
  }

  componentWillMount () {
    aquaSingleAnimeRecommednations
      .fetchRecommendations(this.state.animedbId)
      .then(r => {
        this.setState({
          anime: r.animeDetails,
          recommendations: r.recommendations
        })
      })
  }

  render () {
    let recommendations = this.state.recommendations
    let emptyRecommendations =
      recommendations &&
      recommendations.airing.length == 0 &&
      recommendations.completed.length == 0

    return (
      <div>
        <NoUserHeader />
        {this.state.anime && <AnimeDetails anime={this.state.anime} />}
        {recommendations &&
          !emptyRecommendations && (
            <AnimeRecommendations recommendations={recommendations} />
          )}
        {!this.state.anime && <Spinner />}
        {emptyRecommendations && (
          <div className='aqua-body'>
            There are no recommendations for this anime
          </div>
        )}
      </div>
    )
  }
}

export default AnimePage
