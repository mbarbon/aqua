// @flow
import React from 'react'
import ReactDOM from 'react-dom'
import './index.css'
import App from './App'
import registerServiceWorker from './registerServiceWorker'
import '../../../../src/main/resources/public/css/aqua.css'

ReactDOM.render(<App />, window.document.getElementById('root'))
registerServiceWorker()
