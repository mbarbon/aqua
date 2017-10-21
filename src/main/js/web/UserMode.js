// @flow
import React, { Component } from 'react'
import { localState } from '../shared/state/Globals'

type Props = {
  queuePosition: ?number
}

type State = {
  malUserName: string
}

export default class UserMode extends Component<Props, State> {
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

  render () {
    return (
      <div className='start-page' id='start-page'>
        <div className='start-page-body'>
          <img
            src='aqua-thumbsup.jpg'
            alt='The goddess Aqua showing approbation'
          />
          <div className='start-page-head'>
            <div className='first-head'>Aqua</div>
            <div className='second-head'>anime recommendations</div>
          </div>
          <br style={{ clear: 'both' }} />
        </div>
        <div className='start-your-list'>
          MAL user{' '}
          <input
            value={this.state.malUserName}
            onChange={this.updateUserName.bind(this)}
            onKeyDown={this.handleEnter.bind(this)}
          />
          {' or '}
          <a href='add-anime' onClick={this.setLocalUser.bind(this)}>
            just add anime you like
          </a>
        </div>
        {this.props.queuePosition && (
          <div className='start-your-list'>
            Loading MAL anime list (position in queue {this.props.queuePosition})
          </div>
        )}
      </div>
    )
  }
}
