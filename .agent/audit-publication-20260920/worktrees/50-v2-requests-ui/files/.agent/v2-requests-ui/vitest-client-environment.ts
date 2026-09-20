export default {
  name: 'rct-vue-client',
  viteEnvironment: 'client',
  setup() {
    return {
      teardown() {
        // The component test uses its own host renderer and needs no DOM globals.
      },
    }
  },
}
