import { rspack } from '@rspack/core'
import { ReactRefreshRspackPlugin } from '@rspack/plugin-react-refresh'

export default (env = {}) => {
  const isDev = process.env.NODE_ENV
    ? process.env.NODE_ENV === 'development'
    : process.argv.includes('dev')
  const useMock = env.BYTE_MALL_USE_MOCK === true
    || env.BYTE_MALL_USE_MOCK === 'true'
    || process.env.BYTE_MALL_USE_MOCK === 'true'

  return {
    mode: isDev ? 'development' : 'production',
    context: import.meta.dirname,
    target: 'web',
    entry: './src/main.tsx',
    devtool: isDev ? 'eval-cheap-module-source-map' : false,
    output: {
      path: `${import.meta.dirname}/dist`,
      publicPath: '/',
      clean: true,
      filename: isDev ? 'assets/js/[name].js' : 'assets/js/[name].[contenthash].js',
      chunkFilename: isDev ? 'assets/js/[name].chunk.js' : 'assets/js/[name].[contenthash].chunk.js',
      cssFilename: isDev ? 'assets/css/[name].css' : 'assets/css/[name].[contenthash].css',
      cssChunkFilename: isDev ? 'assets/css/[name].chunk.css' : 'assets/css/[name].[contenthash].chunk.css',
    },
    resolve: {
      extensions: ['.tsx', '.ts', '.jsx', '.js', '.json'],
      alias: { '@': `${import.meta.dirname}/src` },
    },
    module: {
      rules: [
        {
          test: /\.(?:js|mjs|jsx|ts|tsx)$/,
          exclude: /node_modules/,
          use: {
            loader: 'builtin:swc-loader',
            options: {
              detectSyntax: 'auto',
              jsc: {
                target: 'es2022',
                transform: {
                  react: {
                    runtime: 'automatic',
                    development: isDev,
                    refresh: isDev,
                  },
                },
              },
            },
          },
        },
        { test: /\.css$/i, type: 'css/auto' },
      ],
    },
    plugins: [
      new rspack.HtmlRspackPlugin({
        template: './index.html',
        scriptLoading: 'defer',
      }),
      new rspack.CopyRspackPlugin({
        patterns: [{ from: 'public', to: '.' }],
      }),
      new rspack.DefinePlugin({
        __BYTE_MALL_API_BASE_URL__: JSON.stringify(process.env.BYTE_MALL_API_BASE_URL),
        __BYTE_MALL_USE_MOCK__: JSON.stringify(useMock),
      }),
      ...(isDev
        ? [new ReactRefreshRspackPlugin(), new rspack.HotModuleReplacementPlugin()]
        : []),
    ],
    optimization: {
      splitChunks: {
        chunks: 'all',
        cacheGroups: {
          framework: {
            test: /[\\/]node_modules[\\/](?:react|react-dom|scheduler|react-router|react-router-dom)[\\/]/,
            name: 'framework',
            priority: 30,
            enforce: true,
          },
        },
      },
    },
    devServer: {
      host: '127.0.0.1',
      port: 5173,
      open: false,
      hot: true,
      historyApiFallback: true,
      static: { directory: `${import.meta.dirname}/public` },
    },
    stats: 'errors-warnings',
  }
}
