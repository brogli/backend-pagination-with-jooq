import './assets/main.css'

import { createApp } from 'vue'
import OpenVue from 'openvue/config'

import App from './App.vue'
import router from './router'
import { AppPreset } from './theme/preset'

const app = createApp(App)

app.use(router)
app.use(OpenVue, {
  theme: {
    preset: AppPreset,
    options: {
      cssLayer: {
        name: 'openvue',
        order: 'theme, base, openvue, components, utilities',
      },
    },
  },
})

app.mount('#app')
