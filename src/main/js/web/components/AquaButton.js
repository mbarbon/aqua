// @flow
import React, { PureComponent } from 'react'

type Props = {
  small: boolean,
  inline: boolean,
  href: string,
  label: string,
  onClick: void => void
}

export default class AquaButton extends PureComponent<Props> {
  static defaultProps = {
    href: '#',
    small: false,
    inline: false
  }

  render () {
    let classNames = ['aqua-button']
    if (this.props.inline) {
      classNames.push('inline-aqua-button')
    }
    if (this.props.small) {
      classNames.push('small-aqua-button')
    }

    return (
      <div className={classNames.join(' ')}>
        <a
          href={this.props.href}
          onClick={e => {
            e.preventDefault()
            this.props.onClick()
          }}
        >
          {this.props.label}
        </a>
      </div>
    )
  }
}
