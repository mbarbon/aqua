// @flow
import React, { PureComponent } from 'react'
import Spinner from './components/Spinner'
import { NoUserHeader } from './Header'
import { AnimeList } from './components/AnimeList'
import { aquaAnimeList } from '../shared/state/Globals'

import type { Anime } from '../shared/backend/types'
import type { AnimeListExtractItem } from '../shared/backend/AquaAnimeList'

function ignoreClick (anime) {}
function makeAnimeLink (anime) {
  return `/anime/details/${anime.animedbId}`
}

type AllAnimeState = {
  parts: Array<AnimeListExtractItem>
}

class AllAnime extends PureComponent<{}, AllAnimeState> {
  state = { parts: [] }

  componentWillMount () {
    aquaAnimeList.fetchAnimeListExtract().then(r => {
      this.setState({ parts: r.parts })
    })
  }

  renderParts () {
    return this.state.parts.map(part => (
      <div className='aqua-body' key={'all-anime-part-' + part.headLetter}>
        <h2>
          <a href={'/anime/list/' + part.headLetter}>{part.headLetter}</a>
        </h2>
        <AnimeList
          anime={part.exampleAnime}
          onAnimeClick={ignoreClick}
          makeAnimeLink={makeAnimeLink}
        />
        <a href={'/anime/list/' + part.headLetter}>More...</a>
      </div>
    ))
  }

  render () {
    return (
      <div>
        <NoUserHeader />
        <div className='aqua-body'>
          <h1>Browse all anime</h1>
        </div>
        {this.renderParts()}
      </div>
    )
  }
}

type AllAnimePageProps = {
  match: { params: { animeInitial: string } }
}

type AllAnimePageState = {
  animeInitial: string,
  anime: ?Array<Anime>
}

class AllAnimePage extends PureComponent<AllAnimePageProps, AllAnimePageState> {
  constructor (props: AllAnimePageProps) {
    super(props)

    this.state = {
      animeInitial: props.match.params.animeInitial,
      anime: null
    }
  }

  componentWillMount () {
    aquaAnimeList.fetchAnimeList(this.state.animeInitial).then(r => {
      this.setState({ anime: r.anime })
    })
  }

  render () {
    let anime = this.state.anime

    return (
      <div>
        <NoUserHeader />
        {anime ? (
          <div className='aqua-body'>
            <h1>
              {anime.length} anime starting with '{this.state.animeInitial}'
            </h1>
            <AnimeList
              anime={anime}
              onAnimeClick={ignoreClick}
              makeAnimeLink={makeAnimeLink}
            />
          </div>
        ) : (
          <Spinner />
        )}
      </div>
    )
  }
}

export { AllAnime, AllAnimePage }
