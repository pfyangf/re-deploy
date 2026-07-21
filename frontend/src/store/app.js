import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    theme: localStorage.getItem('theme') || 'dark',
    sidebarCollapsed: false,
  }),

  getters: {
    isDark: (state) => state.theme === 'dark',
  },

  actions: {
    toggleTheme() {
      this.theme = this.theme === 'dark' ? 'light' : 'dark'
      localStorage.setItem('theme', this.theme)
      document.documentElement.setAttribute('class', this.theme)
    },

    setTheme(theme) {
      this.theme = theme
      localStorage.setItem('theme', theme)
      document.documentElement.setAttribute('class', theme)
    },

    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },
  },
})
