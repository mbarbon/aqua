// @flow
// XXX: shared
import { PubSub1 } from '../helpers/PubSub'
import DelayedAction from '../helpers/DelayedAction'
import type { Anime } from './types'

let objectionable = {
  '1853': true,
  '35288': true
}

function filterObjectionableContent (anime) {
  return anime.filter(a => !objectionable[a.animedbId])
}

function fetchCompletions (term: string): Promise<Array<Anime>> {
  let encoded = encodeURIComponent(term)
  let headers = new Headers()

  headers.append('Cache-Control', 'max-age=' + 3600 * 6)
  headers.append('Cache-Control', 'max-stale')

  return fetch('/autocomplete?term=' + encoded, {
    headers: headers
  }).then(response => response.json())
}

export default class AquaAutocomplete {
  term: ?string
  completions: ?Array<Anime>
  pubSub: PubSub1<Array<Anime>>
  delay: DelayedAction

  constructor () {
    this.term = null
    this.completions = null
    this.pubSub = new PubSub1()
    this.delay = new DelayedAction()
  }

  setTerm (term: string) {
    this.term = term

    if (this.term.length) {
      this.delay.setAction(250, this._fetchCompletions.bind(this, this.term))
    } else {
      this.delay.cancel()
      this.completions = []
      this.pubSub.notify(this.completions)
    }
  }

  getAnime () {
    return this.completions
  }

  _fetchCompletions (term: string) {
    fetchCompletions(term)
      .then(completions => {
        this.completions = filterObjectionableContent(completions)
        this.pubSub.notify(this.completions)
      })
      .catch(error => {
        console.error(error)
      })
  }
}
