import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Button,
  MenuItem,
  TextField,
  CircularProgress,
} from "@mui/material";
import { api, errorText } from "../../../../campus/api";
import { useAuth } from "../../../../campus/useAuth";
import { cancellation, money, time, label } from "../../../../campus/types";
import type { Listing } from "../../../../campus/types";
import { ItemArt } from "../ProductCard/ProductCard";
export default function ProductDetails() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [item, setItem] = useState<Listing | null>(null),
    [slot, setSlot] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    api
      .get<Listing>("/listings/" + id)
      .then((r) => setItem(r.data))
      .catch((e) => setError(errorText(e)));
  }, [id]);
  const reserve = async () => {
    if (!user) {
      navigate("/signin");
      return;
    }
    setBusy(true);
    try {
      await api.post("/reservations", {
        productId: Number(id),
        pickupSlot: slot,
      });
      navigate("/reservations");
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <>
      <Link to="/" className="back">
        ← Back to all finds
      </Link>
      {error && <Alert severity="error">{error}</Alert>}
      {!item ? (
        !error && <CircularProgress />
      ) : (
        <section className="detail">
          <ItemArt item={item} />
          <div>
            <div className="eyebrow">
              {item.category} · {item.condition}
            </div>
            <h1>{item.title}</h1>
            <div className="detail-price">{money(item.priceCents)}</div>
            <p className="status">{label(item.status)}</p>
            <p>{item.description}</p>
            <p>
              <strong>{item.apartment}</strong> · {item.campus}
              <br />
              {item.pickupArea}
            </p>
            <p className="muted">
              Listed by {item.sellerName}. Exact pickup address is shared with
              the participants after reservation is confirmed.
            </p>
            <div className="payment-box">
              <div>
                <span>Deposit now</span>
                <strong>{money(item.depositCents)}</strong>
              </div>
              <div>
                <span>Balance at pickup</span>
                <strong>{money(item.priceCents - item.depositCents)}</strong>
              </div>
              <p>{cancellation}</p>
              {item.depositCents > 0 && (
                <p>
                  Your item is temporarily held while payment is pending. Check
                  the deadline in My pickups. Late payments are refunded and do
                  not re-reserve the item.
                </p>
              )}
            </div>
            <TextField
              select
              fullWidth
              label="Choose a pickup time"
              value={slot}
              onChange={(e) => setSlot(e.target.value)}
            >
              {item.pickupSlots
                .filter((s) => new Date(s) > new Date())
                .map((s) => (
                  <MenuItem key={s} value={s}>
                    {time(s)}
                  </MenuItem>
                ))}
            </TextField>
            <Button
              fullWidth
              size="large"
              variant="contained"
              sx={{ mt: 2 }}
              disabled={
                busy ||
                !slot ||
                item.status !== "AVAILABLE" ||
                item.ownerId === user?.id
              }
              onClick={reserve}
            >
              {item.ownerId === user?.id
                ? "Your listing"
                : user
                  ? "Reserve for pickup"
                  : "Sign in to reserve"}
            </Button>
          </div>
        </section>
      )}
    </>
  );
}
