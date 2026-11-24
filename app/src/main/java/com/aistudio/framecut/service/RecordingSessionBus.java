package com.aistudio.framecut.service;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.aistudio.framecut.model.RecordingSessionState;

public final class RecordingSessionBus {
    private static final RecordingSessionBus INSTANCE = new RecordingSessionBus();
    private final MutableLiveData<RecordingSessionState> sessionLiveData =
            new MutableLiveData<>(new RecordingSessionState());

    private RecordingSessionBus() {}

    public static RecordingSessionBus getInstance() {
        return INSTANCE;
    }

    public LiveData<RecordingSessionState> getSessionLiveData() {
        return sessionLiveData;
    }

    public RecordingSessionState getCurrentState() {
        RecordingSessionState current = sessionLiveData.getValue();
        return current != null ? current.copy() : new RecordingSessionState();
    }

    public void postState(RecordingSessionState next) {
        sessionLiveData.postValue(next.copy());
    }
}
