import './assets/main.css'
import 'primeicons/primeicons.css'

import { createApp } from 'vue'
import PrimeVue from 'primevue/config'

import App from './App.vue'
import router from './router'
import { AppPreset } from './theme/preset'

const app = createApp(App)

app.use(router)
app.use(PrimeVue, {
  theme: {
    preset: AppPreset,
    options: {
      cssLayer: {
        name: 'primevue',
        order: 'theme, base, primevue, components, utilities',
      },
    },
  },
})

app.mount('#app')
