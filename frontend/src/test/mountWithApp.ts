import { mount, type ComponentMountingOptions } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import {
  createMemoryHistory,
  createRouter,
  type RouteRecordRaw,
  type Router,
} from 'vue-router'
import { AppPreset } from '@/theme/preset'

const stubRoute: RouteRecordRaw = {
  path: '/',
  component: { template: '<div />' },
}

type MountResult<T> = ReturnType<typeof mount<T>>

export async function mountWithApp<T>(
  component: T,
  options: ComponentMountingOptions<T> = {},
  initialPath = '/',
): Promise<{ wrapper: MountResult<T>; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [stubRoute],
  })
  await router.push(initialPath)
  await router.isReady()

  // why: TS doesn't unify mount's inferred return shape with ReturnType<typeof mount<T>>
  // across this generic boundary, even though the values are identical.
  const wrapper = mount(component, {
    ...options,
    global: {
      ...options.global,
      plugins: [
        ...(options.global?.plugins ?? []),
        router,
        [PrimeVue, { theme: { preset: AppPreset } }],
      ],
    },
  }) as MountResult<T>

  return { wrapper, router }
}
