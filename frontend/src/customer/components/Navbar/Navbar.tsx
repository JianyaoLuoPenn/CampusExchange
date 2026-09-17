import { Link, NavLink, useNavigate } from "react-router-dom";
import { Button } from "@mui/material";
import { useAuth } from "../../../campus/useAuth";
export default function Navbar() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  return (
    <header className="navbar">
      <Link className="brand" to="/">
        <span className="brand-mark">CE</span>CampusExchange
        <span className="beta">BETA</span>
      </Link>
      <nav>
        <NavLink to="/">Explore</NavLink>
        <NavLink to="/reservations">My pickups</NavLink>
        <Button component={Link} to="/sell" variant="contained">
          + List an item
        </Button>
        {user ? (
          <Button
            onClick={() => {
              signOut();
              navigate("/");
            }}
          >
            Sign out · {user.name.split(" ")[0]}
          </Button>
        ) : (
          <Button component={Link} to="/signin">
            Sign in
          </Button>
        )}
      </nav>
    </header>
  );
}
