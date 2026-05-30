import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomePage.vue')
    },
    {
      path: '/analysis',
      name: 'analysis',
      component: () => import('@/views/AnalysisPage.vue')
    },
    {
      path: '/history',
      name: 'history',
      component: () => import('@/views/HistoryPage.vue')
    }
  ]
})

export default router
