// @flow
import React, { Component } from 'react'
import { localState, aquaRecommendations } from '../shared/state/Globals'
import AquaButton from './components/AquaButton'

const recommendationsValidity = 24 * 3600

type Props = {}

type State = {
  userMode: ?('mal' | 'local' | 'loading')
}

class AnimeDetails extends Component<Props, State> {
  userModeChangedCallback: () => void

  constructor (props: Props) {
    super(props)
    this.state = {
      userMode: 'loading'
    }
    this.userModeChangedCallback = this.userModeChanged.bind(this)
  }

  componentWillMount () {
    aquaRecommendations.pubSub.userMode.subscribe(this.userModeChangedCallback)
    localState
      .loadUserMode(recommendationsValidity)
      .then(() => {
        this.setState({ userMode: aquaRecommendations.getUserMode() })
      })
      .catch(error => {
        console.error(error)
        this.setState({ userMode: null })
      })
  }

  componentWillUnmount () {
    aquaRecommendations.pubSub.userMode.unsubscribe(
      this.userModeChangedCallback
    )
  }

  userModeChanged () {
    let userMode = aquaRecommendations.getUserMode()
    this.setState({ userMode })
  }

  render () {
    if (this.state.userMode == 'local') {
      return (
        <div className='main-page-head-actions'>
          <AquaButton
            label='Recommendations'
            onClick={() => {
              window.location.href = '/'
            }}
          />
        </div>
      )
    } else {
      return null
    }
  }
}

export default AnimeDetails
