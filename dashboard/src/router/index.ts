import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'Dashboard',
      component: DashboardView,
    },
    {
      path: '/positions',
      name: 'Positions',
      component: () => import('../views/PositionsView.vue'),
    },
    {
      path: '/signals',
      name: 'Signals',
      component: () => import('../views/SignalsView.vue'),
    },
    {
      path: '/portfolio',
      name: 'Portfolio',
      component: () => import('../views/PortfolioView.vue'),
    },
    {
      path: '/watchlist',
      name: 'Watchlist',
      component: () => import('../views/WatchlistView.vue'),
    },
    {
      path: '/backtest',
      name: 'Backtest',
      component: () => import('../views/BacktestView.vue'),
    },
    {
      path: '/sentiment',
      name: 'Sentiment',
      component: () => import('../views/SentimentView.vue'),
    },
    {
      path: '/monitoring',
      name: 'Monitoring',
      component: () => import('../views/MonitoringView.vue'),
    },
    {
      path: '/data',
      name: 'Data Ingestion',
      component: () => import('../views/DataIngestionView.vue'),
    },
    {
      path: '/settings',
      name: 'Settings',
      component: () => import('../views/SettingsView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('../views/NotFoundView.vue'),
    },
  ],
})

export default router
