// @flow
import React, { PureComponent } from 'react'
import { aquaAutocomplete, localState } from '../shared/state/Globals'
import AquaButton from './components/AquaButton'

export default class Header extends PureComponent<{
  userMode: string,
  malUsername: ?string,
  queuePosition: ?number,
  canRefresh: boolean
}> {
  changeUser () {
    localState.resetUserMode()
  }

  refreshMalList () {
    let malUsername = this.props.malUsername
    if (malUsername == null) {
      throw new Error('Invalid calls to refreshMailLis')
    }
    localState.loadMalRecommendations(malUsername)
  }

  refreshLocalRecommendations () {
    localState.loadLocalRecommendations()
    aquaAutocomplete.setTerm('')
  }

  render () {
    let isMal = !!this.props.malUsername

    return (
      <div className='main-page-head aqua-body' id='main-page-head'>
        <div className='main-page-head-actions'>
          {this.props.userMode === 'local' && (
            <AquaButton
              inline
              onClick={this.changeUser.bind(this)}
              label='Use a MAL account'
            />
          )}
          {isMal && <b>{this.props.malUsername}</b>}
          {this.props.queuePosition &&
            `(position in queue ${this.props.queuePosition}`}
          {this.props.canRefresh &&
            !this.props.queuePosition && (
              <AquaButton
                inline
                small
                onClick={
                  isMal
                    ? this.refreshMalList.bind(this)
                    : this.refreshLocalRecommendations.bind(this)
                }
                label='Refresh'
              />
            )}{' '}
          {this.props.userMode === 'mal' && (
            <AquaButton
              inline
              small
              href='change-user'
              onClick={this.changeUser.bind(this)}
              label='Change user'
            />
          )}
        </div>
        <div className='main-page-logo'>
          <img src='aqua-thumbsup.jpg' />
          <div className='start-page-head-text'>
            <div className='first-head'>Aqua</div>
            <div className='second-head'>anime recommendations</div>
          </div>
        </div>
      </div>
    )
  }
}
