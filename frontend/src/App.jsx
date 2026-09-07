import { BrowserRouter } from "react-router-dom";
import { Provider } from "react-redux";
import { store } from "./app/store";
import { AuthProvider } from "./context/AuthContext";
import { ToastProvider } from "./context/ToastContext";
import AppRoutes from "./routes/AppRoutes";

export default function App() {
  return (
    <Provider store={store}>
      <BrowserRouter>
        <AuthProvider>
          <ToastProvider>
            <AppRoutes />
          </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    </Provider>
  );
}
