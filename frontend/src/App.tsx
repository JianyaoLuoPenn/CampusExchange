import "./App.css";
import { ThemeProvider } from "@mui/material/styles";
import customeTheme from "./Theme/customeTheme";
import { Route, Routes, Navigate } from "react-router-dom";
import { Alert } from "@mui/material";
import { useEffect, useState } from "react";
import Navbar from "./customer/components/Navbar/Navbar";
import Products from "./customer/pages/Products/Products";
import ProductDetails from "./customer/pages/Products/ProductDetails/ProductDetails";
import Reservations from "./customer/pages/Account/Reservations";
import Publish from "./seller/pages/Products/CampusPublish";
import SignIn from "./campus/SignIn";
import { AuthProvider } from "./campus/Auth";
import { api } from "./campus/api";
function App() {
  const [mode, setMode] = useState("loading");
  useEffect(() => {
    api
      .get("/config")
      .then((r) => setMode(r.data.paymentMode))
      .catch(() => setMode("offline"));
  }, []);
  return (
    <ThemeProvider theme={customeTheme}>
      <AuthProvider>
        <Navbar />
        <div className="mode-banner">
          {mode === "mock"
            ? "SIMULATION MODE · No real payment or refund is processed"
            : mode === "stripe"
              ? "STRIPE TEST MODE · Test payments only"
              : mode === "offline"
                ? "Server unavailable · Check your backend connection"
                : "Connecting to CampusExchange…"}
        </div>
        <main>
          <Routes>
            <Route path="/" element={<Products />} />
            <Route path="/listings/:id" element={<ProductDetails />} />
            <Route path="/sell" element={<Publish />} />
            <Route path="/reservations" element={<Reservations />} />
            <Route path="/signin" element={<SignIn />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <footer>
          CampusExchange · A learning project. Demo people, apartments and
          listings are fictional.
          <br />
          Built on Ecommerce Multi Vendor Project by Code With Zosh.{" "}
          <span>Meet locally. Give useful things another chapter.</span>
        </footer>
        {mode === "offline" && (
          <Alert severity="warning">
            The API is unavailable. Listings and payments require a running
            backend.
          </Alert>
        )}
      </AuthProvider>
    </ThemeProvider>
  );
}
export default App;
