import { createSlice } from "@reduxjs/toolkit";
import { getPersistedUser } from "../services/authService";

const initialState = {
  user: getPersistedUser(),
  isAuthenticated: !!getPersistedUser(),
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setCredentials(state, action) {
      state.user = action.payload;
      state.isAuthenticated = true;
    },
    logout(state) {
      state.user = null;
      state.isAuthenticated = false;
    },
  },
});

export const { setCredentials, logout } = authSlice.actions;
export default authSlice.reducer;
