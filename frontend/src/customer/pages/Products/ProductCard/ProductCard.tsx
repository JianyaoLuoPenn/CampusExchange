import { Link } from 'react-router-dom';
import type { Listing } from '../../../../campus/types';
import { money, label } from '../../../../campus/types';
import ChairOutlinedIcon from '@mui/icons-material/ChairOutlined';
import DevicesOutlinedIcon from '@mui/icons-material/DevicesOutlined';
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined';
import KitchenOutlinedIcon from '@mui/icons-material/KitchenOutlined';
export function ItemArt({item}:{item:Listing}) { const Icon=item.category==='Furniture'?ChairOutlinedIcon:item.category==='Electronics'?DevicesOutlinedIcon:item.category==='Textbooks'?MenuBookOutlinedIcon:KitchenOutlinedIcon;
 return <div className={'item-art art-'+item.category.split(' ')[0].toLowerCase()}>{item.images[0]?<img src={item.images[0]} alt={item.title} loading="lazy" referrerPolicy="no-referrer"/>:<Icon aria-hidden="true"/>}<span className="condition">{item.condition}</span></div>; }
export default function ProductCard({item}:{item:Listing}) { return <Link to={'/listings/'+item.id} className="product-card"><ItemArt item={item}/><div className="card-body"><div className="eyebrow">{item.category} <span className={'status '+item.status}>{label(item.status)}</span></div><h3>{item.title}</h3><strong className="price">{money(item.priceCents)}</strong><p>{item.apartment} · {item.campus}</p><div className="card-foot"><span>{item.sellerName}</span><span>{item.depositCents?money(item.depositCents)+' deposit':'No deposit'}</span></div></div></Link>; }
