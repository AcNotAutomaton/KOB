import { createRouter, createWebHistory } from 'vue-router'
const HomeIndexView = () => import('../views/home/HomeIndexView.vue')
const PkIndexView = () => import('../views/pk/PkIndexView.vue')
const RecordIndexView = () => import('../views/record/RecordIndexView.vue')
const RecordContentView = () => import('../views/record/RecordContentView.vue')
const RanklistIndexView = () => import('../views/ranklist/RanklistIndexView.vue')
const RanklistIndexBot = () => import('../views/ranklist/RanklistindexBot.vue')
const UserBotIndexView = () => import('../views/user/bot/UserBotIndexView.vue')
const NotFound = () => import('../views/error/NotFound.vue')
const UserAccountLoginView = () => import('../views/user/account/UserAccountLoginView.vue')
const UserAccountRegisterView = () => import('../views/user/account/UserAccountRegisterView.vue')
const UserMainView = () => import('../views/user/account/UserMainView.vue')

import store from '../store/index'

const routes = [
  {
    path: "/",
    name: "home_index",
    component: HomeIndexView,
    meta: {
      requestAuth: true,
    }
  },
  {
    path: "/pk/",
    name: "pk_index",
    component: PkIndexView,
    meta: {
      requestAuth: true,
    }
  },
  {
    path: "/record/",
    name: "record_index",
    component: RecordIndexView,
    meta: {
      requestAuth: true,
    }
  },
  {
    path: "/record/:recordId/",
    name: "record_content",
    component: RecordContentView,
    meta: {
      requestAuth: true,
    }
  },
  {
    path: "/ranklist/",
    name: "ranklist_index",
    component: RanklistIndexView,
    meta: {
      requestAuth: true,
    }
  },
  {
    path: "/botranklist/",
    name: "bot_ranklist_index",
    component: RanklistIndexBot,
    meta: {
      requestAuth: true,
    }
  },
  {
    path: "/user/bot/",
    name: "user_bot_index",
    component: UserBotIndexView,
    meta: {
      requestAuth: true,
    }
  },
  {
    path: "/users/:id/",
    name: "user_name_index",
    component: UserMainView,
    props: true,
    meta: {
      requestAuth: true,
    }
  },
  {
    path: "/user/account/login/",
    name: "user_account_login",
    component: UserAccountLoginView,
    meta: {
      requestAuth: false,
    }
  },
  {
    path: "/user/account/register/",
    name: "user_account_register",
    component: UserAccountRegisterView,
    meta: {
      requestAuth: false,
    }
  },
  {
    path: "/404/",
    name: "404",
    component: NotFound,
    meta: {
      requestAuth: false,
    }
  },
  {
    path: "/:catchAll(.*)",
    redirect: "/404/"
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async to => {
  if (store.state.user.pulling_info) await store.dispatch("restoreLogin");
  if (to.meta.requestAuth && !store.state.user.is_login) {
    return { name: "user_account_login", query: { redirect: to.fullPath }, replace: true };
  }
  if (to.name === "user_account_login" && store.state.user.is_login) return { name: "home_index" };
})

export default router
