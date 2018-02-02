// @flow
import React, { Component } from 'react'
import AquaButton from './components/AquaButton'
import Spinner from './components/Spinner'
import { localState } from '../shared/state/Globals'

type Props = {
  queuePosition: ?number,
  loadingState: number
}

type State = {
  malUserName: string
}

export default class UserMode extends Component<Props, State> {
  static MODE_DEFAULT = 0
  static MODE_LOADING_LIST = 1
  static MODE_LOADING_RECOMMENDATIONS = 2
  static MODE_PRERENDER = 3

  constructor (props: Props) {
    super(props)
    this.state = {
      malUserName: ''
    }
  }

  updateUserName (e: SyntheticInputEvent<HTMLInputElement>) {
    this.setState({ malUserName: e.target.value })
  }

  handleEnter (e: SyntheticKeyboardEvent<HTMLInputElement>) {
    if (e.keyCode === 13) {
      e.preventDefault()
      localState.setMalUsernameAndLoadRecommendations(this.state.malUserName)
    }
  }

  setLocalUser (e: SyntheticEvent<>) {
    e.preventDefault()
    localState.setLocalUserAndLoadRecommendations()
  }

  stopLoading () {
    localState.resetUserMode()
  }

  render () {
    let loadingState = this.props.loadingState

    return (
      <div className='start-page' id='start-page'>
        <div className='start-page-body'>
          <img
            src='/aqua-thumbsup.jpg'
            alt='The goddess Aqua showing approbation'
          />
          <div className='start-page-head'>
            <div className='first-head'>Aqua</div>
            <div className='second-head'>anime recommendations</div>
          </div>
          <br style={{ clear: 'both' }} />
        </div>
        {loadingState == UserMode.MODE_DEFAULT && (
          <div className='start-your-list'>
            <div>
              Use your MAL user{' '}
              <input
                className='text-input'
                value={this.state.malUserName}
                onChange={this.updateUserName.bind(this)}
                onKeyDown={this.handleEnter.bind(this)}
              />
            </div>
            <div>
              {'or '}
              <a href='add-anime' onClick={this.setLocalUser.bind(this)}>
                just add anime you like
              </a>
            </div>
            <div>
              {'you can also '}
              <a href='/anime/list'>browse all anime</a>
            </div>
          </div>
        )}
        {loadingState != UserMode.MODE_DEFAULT && <Spinner />}
        {loadingState == UserMode.MODE_LOADING_LIST && (
          <div className='start-your-list'>
            Loading MAL anime list (position in queue {this.props.queuePosition})
          </div>
        )}
        {loadingState == UserMode.MODE_LOADING_RECOMMENDATIONS && (
          <div className='start-your-list'>Loading recommendations</div>
        )}
        {loadingState != UserMode.MODE_DEFAULT &&
          loadingState != UserMode.MODE_PRERENDER && (
            <AquaButton
              inline={true}
              label='Cancel'
              onClick={this.stopLoading.bind(this)}
            />
          )}
      </div>
    )
  }
}
