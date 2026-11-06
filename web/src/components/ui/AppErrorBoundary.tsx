import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'

interface State {
  error: Error | null
}

export class AppErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ByteMall render error', error, info.componentStack)
  }

  render() {
    if (this.state.error) {
      return <main className="container page-section"><div className="ui-state ui-error" role="alert"><h1>页面暂时无法显示</h1><p>{this.state.error.message}</p><button className="button button-primary" type="button" onClick={() => window.location.assign('/')}>返回首页</button></div></main>
    }
    return this.props.children
  }
}
