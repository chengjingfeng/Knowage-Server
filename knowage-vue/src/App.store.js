import { createStore } from 'vuex'

// Create a new store instance.
const store = createStore({
	state() {
		return {
			user: {},
			error: {},
			locale: "it_IT",
			download: false,
		}
	},
	mutations: {
		setUser(state, user) {
			state.user = user
		},
		setError(state, msg) {
			state.error = { visible: true, msg: msg }
		},
		setLocale(state, locale) {
			state.locale = locale
		},
		setDownload(state, hasDownload) {
			state.download = hasDownload
		}
	}
})

export default store