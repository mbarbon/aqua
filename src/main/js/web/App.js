// @flow
import React, { PureComponent } from 'react'
import { BrowserRouter, Route, Switch } from 'react-router-dom'
import Main from './Main'
import AnimePage from './AnimePage'
import { AllAnime, AllAnimePage } from './AllAnime'

class App extends PureComponent<{}> {
  render () {
    return (
      <BrowserRouter>
        <Switch>
          <Route exact path='/' component={Main} />
          <Route path='/anime/details/:animedbId' component={AnimePage} />
          <Route path='/anime/list/:animeInitial' component={AllAnimePage} />
          <Route path='/anime/list' component={AllAnime} />
        </Switch>
      </BrowserRouter>
    )
  }
}

export default App
