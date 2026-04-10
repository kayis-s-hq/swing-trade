import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
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
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

export default router
