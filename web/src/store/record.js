export default {
    state: {
        is_record: false,
        a_steps: "",
        b_steps: "",
        record_loser: "",
        progress_bar:0,
    },
    getters: {
    },
    mutations: {
        updateProgress(state, progress_bar){
            state.progress_bar = progress_bar
        },
        updateIsRecord(state, is_record) {
            state.is_record = is_record;
        },
        updateSteps(state, data) {
            state.a_steps = data.a_steps;
            state.b_steps = data.b_steps;
        },
        updateRecordLoser(state, loser) {
            state.record_loser = loser;
        }
    },
    actions: {
    },
    modules: {
    }
}