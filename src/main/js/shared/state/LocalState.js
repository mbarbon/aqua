// @flow
import { AsyncStorage } from './AsyncStorage'
import { aquaRecommendations, localAnimeList } from './Globals'
import type { Anime, Rating, Recommendations } from '../backend/types'
import type { FilterState, LocalAnime, StorageType } from './types'

const malUsernameKey = '@Aqua:mal:username'
const userModeKey = '@Aqua:userMode'
const localAnimeListKey = '@Aqua:local:animeList'
const localListChangedKey = '@Aqua:local:animeListChanged'
const malAnimeListKey = '@Aqua:mal:animeList'
const cachedRecommendationsKey = '@Aqua:recommendations:cache'
const cachedRecommendationsTimeKey = '@Aqua:recommendations:cacheTime'
const cachedRecommendationsForKey = '@Aqua:recommendations:cacheFor'
const filterStateKey = '@Aqua:recommendationFilters'

let objectionable = {
  '1853': true,
  '35288': true
}

function removeObjectionableContent (animeList) {
  return animeList.filter(anime => !objectionable[anime.animedbId])
}

export default class LocalState {
  currentFilterState: FilterState

  constructor () {
    this.currentFilterState = {}
  }

  resetUserMode () {
    AsyncStorage.multiRemove([malUsernameKey, userModeKey]).then(() =>
      aquaRecommendations.clearMalUsername()
    )
  }

  setLocalUser () {
    AsyncStorage.setItem(userModeKey, 'local').then(() =>
      aquaRecommendations.setLocalUser()
    )
  }

  setLocalUserAndLoadRecommendations () {
    AsyncStorage.setItem(userModeKey, 'local').then(() => {
      aquaRecommendations.setLocalUser()
      return this._loadLocalAnimeList(true)
    })
  }

  setMalUsernameAndLoadRecommendations (username: string) {
    AsyncStorage.multiSet([
      [malUsernameKey, username],
      [userModeKey, 'mal']
    ]).then(() => {
      aquaRecommendations.setMalUsername(username)
      return this.loadMalRecommendations(username)
    })
  }

  loadMalRecommendations (username: string): Promise<void> {
    return aquaRecommendations
      .loadMalAnimeList()
      .then(animeList => {
        // as a cache
        this.setMalAnimeList(animeList)

        return animeList
      })
      .then(
        aquaRecommendations.loadMalRecommendations.bind(aquaRecommendations)
      )
  }

  loadLocalRecommendations () {
    this._loadLocalAnimeList(true)
  }

  loadUserMode (recommendationExpiration: number) {
    return AsyncStorage.multiGet([
      userModeKey,
      malUsernameKey,
      cachedRecommendationsTimeKey,
      cachedRecommendationsForKey,
      filterStateKey
    ]).then(keys => {
      let now = Date.now() / 1000
      let userMode = keys[0][1]
      let username = keys[1][1]
      let recommendationRefresh = keys[2][1] ? parseInt(keys[2][1]) : 0
      let recommendationMode = keys[3][1]
      let filterState = keys[4][1]

      if (filterState) {
        this.currentFilterState = JSON.parse(filterState)
      }

      if (userMode === null) return null
      else if (userMode !== 'mal' && userMode !== 'local')
        throw 'Invalid user mode ' + userMode

      if (
        recommendationMode === userMode &&
        now - recommendationRefresh < recommendationExpiration
      ) {
        let loadLocal
        if (userMode === 'mal') {
          aquaRecommendations.setMalUsername(username)
        } else if (userMode === 'local') {
          aquaRecommendations.setLocalUser()
          loadLocal = this._loadLocalAnimeList(false)
        }
        let cachedRecommendations = AsyncStorage.getItem(
          cachedRecommendationsKey
        ).then(cachedRecommendationsString => {
          aquaRecommendations.setCachedRecommendations(
            JSON.parse(cachedRecommendationsString),
            recommendationRefresh,
            userMode
          )
        })
        return loadLocal
          ? Promise.all([loadLocal, cachedRecommendations])
          : cachedRecommendations
      } else {
        if (userMode === 'mal') {
          return this._loadMalUsername()
        } else if (userMode === 'local') {
          return this._loadLocalAnimeList(true)
        }
      }
    })
  }

  _loadMalUsername () {
    return AsyncStorage.getItem(malUsernameKey).then(username => {
      aquaRecommendations.setMalUsername(username)

      return this.loadMalRecommendations(username)
    })
  }

  _loadLocalAnimeList (reloadRecommendations: boolean): Promise<void> {
    return AsyncStorage.multiGet([
      localAnimeListKey,
      localListChangedKey
    ]).then(keys => {
      let animeListString = keys[0][1]
      let hasChangesString = keys[1][1]
      let hasChanges = !!hasChangesString && hasChangesString === 'true'
      let animeList = removeObjectionableContent(
        JSON.parse(animeListString) || []
      )

      // order is important here
      aquaRecommendations.setLocalUser()
      localAnimeList.setAnimeList(animeList, reloadRecommendations, hasChanges)
    })
  }

  setLocalAnimeList (animeList: Array<LocalAnime>) {
    let listString = JSON.stringify(animeList)
    return AsyncStorage.multiSet([
      [localAnimeListKey, listString],
      [localListChangedKey, 'true']
    ]).then(() => localAnimeList.setAnimeList(animeList, false, true))
  }

  setMalAnimeList (animeList: Array<Rating>) {
    let listString = JSON.stringify(animeList)
    return AsyncStorage.setItem(malAnimeListKey, listString)
  }

  setCachedRecommendations (
    recommendations: Recommendations,
    updateTime: number,
    userMode: StorageType
  ) {
    return AsyncStorage.multiSet([
      [cachedRecommendationsKey, JSON.stringify(recommendations)],
      [localListChangedKey, 'false'],
      [cachedRecommendationsTimeKey, updateTime.toString()],
      [cachedRecommendationsForKey, userMode]
    ])
  }

  setRecommendationFilterState (filterState: FilterState) {
    let jsonString = JSON.stringify(filterState)
    this.currentFilterState = JSON.parse(jsonString)
    return AsyncStorage.setItem(filterStateKey, jsonString)
  }

  getRecommendationFilterState () {
    return this.currentFilterState
  }
}
