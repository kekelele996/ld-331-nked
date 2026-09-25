import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import Dashboard from './pages/Dashboard.vue';
import AbsenceFill from './pages/AbsenceFill.vue';
import './styles/global.css';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Dashboard },
    { path: '/absence-fill', component: AbsenceFill },
    { path: '/:pathMatch(.*)*', component: Dashboard },
  ],
});

createApp(Dashboard).use(router).use(ElementPlus).mount('#app');
