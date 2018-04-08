// @flow
import React, { PureComponent } from 'react'
import { BrowserRouter, Route, Switch } from 'react-router-dom'
import Main from './Main'
import AnimeDetails from './AnimeDetails'

class App extends PureComponent<{}> {
  render () {
    return (
      <BrowserRouter>
        <Switch>
          <Route exact path='/' component={Main} />
          <Route path='/anime/details/' component={AnimeDetails} />
        </Switch>
      </BrowserRouter>
    )
  }
}

export default App
