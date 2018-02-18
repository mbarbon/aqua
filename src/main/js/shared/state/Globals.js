// @flow
import AquaRecommendations from '../backend/AquaRecommendations'
import AquaAutocomplete from '../backend/AquaAutocomplete'
import LocalAnimeList from './LocalAnimeList'
import LocalState from './LocalState'
import FilteredRecommendations from '../helpers/FilteredRecommendations'

export const aquaRecommendations = new AquaRecommendations()
export const aquaAutocomplete = new AquaAutocomplete()
export const localAnimeList = new LocalAnimeList()
export const localState = new LocalState()
export const filteredRecommendations = new FilteredRecommendations(
  aquaRecommendations
)
