#!/usr/bin/env python3
"""Generate a synthetic sanctions/PEP watchlist and a labelled evaluation dataset.

Deterministic (fixed seed) so the Java evaluation test is reproducible.

Outputs (default: ./data):
  watchlist.csv    entryId,primaryName,aliases,dob,nationality,listType   (aliases are '|' separated)
  watchlist.json   the same entries as a JSON array (to exercise the JSON upload path)
  evaluation.csv   caseId,customerName,dob,nationality,expectedEntryId,variantType
                   expectedEntryId == NONE marks a negative (should NOT match anything)

Usage:  python scripts/generate_watchlist.py [--entries 2000] [--out data] [--seed 42]
"""
from __future__ import annotations

import argparse
import csv
import json
import random
import unicodedata
from dataclasses import dataclass, field
from datetime import date, timedelta
from pathlib import Path

# --- Name pools per nationality: (first names, middle names, last names) -----------------------
POOLS: dict[str, tuple[list[str], list[str], list[str]]] = {
    "RU": (
        ["Aleksandr", "Dmitri", "Sergei", "Vladimir", "Nikolai", "Mikhail", "Andrei", "Yuri", "Igor",
         "Oleg", "Viktor", "Pavel", "Boris", "Gennady", "Konstantin", "Anatoly", "Vasily", "Grigory",
         "Leonid", "Stanislav"],
        ["Ivanovich", "Sergeyevich", "Petrovich", "Nikolayevich", "Viktorovich", "Mikhailovich"],
        ["Petrov", "Ivanov", "Smirnov", "Kuznetsov", "Volkov", "Sokolov", "Popov", "Lebedev", "Kozlov",
         "Novikov", "Morozov", "Orlov", "Belov", "Zaitsev", "Pavlov", "Semenov", "Golubev",
         "Vinogradov", "Bogdanov", "Vorobyov"],
    ),
    "SY": (
        ["Mohammed", "Ahmed", "Abdullah", "Omar", "Khalid", "Hussein", "Youssef", "Ibrahim", "Tariq",
         "Karim", "Faisal", "Hamza", "Bilal", "Samir", "Nabil", "Walid", "Rashid", "Jamal", "Mustafa",
         "Hassan"],
        ["Ali", "Hassan", "Abdul Rahman", "Mahmoud"],
        ["Al-Rashid", "Haddad", "Al-Masri", "Khalil", "Nasser", "Al-Hakim", "Mansour", "Saleh",
         "Barakat", "Darwish", "Al-Tikriti", "Qasim", "Hamdan", "Aziz", "Suleiman", "Shaaban",
         "Al-Zahrani", "Jaber", "Fakhoury", "Kanaan"],
    ),
    "IN": (
        ["Rajesh", "Suresh", "Anil", "Vikram", "Sanjay", "Arvind", "Pradeep", "Ramesh", "Deepak",
         "Manoj", "Priya", "Sunita", "Kavita", "Anjali", "Lakshmi", "Harish", "Gopal", "Nitin"],
        ["Kumar", "Prasad", "Lal", "Chandra", "Mohan"],
        ["Sharma", "Gupta", "Patel", "Reddy", "Iyer", "Nair", "Malhotra", "Chatterjee", "Banerjee",
         "Mehta", "Joshi", "Kulkarni", "Rao", "Agarwal", "Bhatt", "Desai", "Kapoor", "Saxena"],
    ),
    "CN": (
        ["Wei", "Jian", "Ming", "Hui", "Xiaoming", "Jun", "Lei", "Yan", "Fang", "Hong", "Zhiwei",
         "Guang", "Haoran", "Yutong"],
        [],
        ["Wang", "Zhang", "Liu", "Chen", "Yang", "Huang", "Zhao", "Zhou", "Wu", "Xu", "Sun", "Ma",
         "Zhu", "Hu", "Guo"],
    ),
    "MX": (
        ["José", "María", "Jesús", "Andrés", "Raúl", "Sebastián", "Martín", "Ramón", "Joaquín",
         "Álvaro", "Óscar", "Héctor", "Iván", "Nicolás", "Inés", "Sofía"],
        ["Luis", "Ángel", "Carmen", "Antonio"],
        ["García", "Rodríguez", "Martínez", "Hernández", "López", "González", "Pérez", "Sánchez",
         "Ramírez", "Gómez", "Díaz", "Muñoz", "Núñez", "Jiménez", "Peña", "Ibáñez"],
    ),
    "DE": (
        ["Jürgen", "Klaus", "Stefan", "Dieter", "Günter", "Jörg", "Matthias", "Wolfgang"],
        ["Friedrich", "Johann"],
        ["Müller", "Schröder", "Weiß", "Köhler", "Bäcker", "Groß", "Krüger", "Hoffmann"],
    ),
    "PL": (
        ["Łukasz", "Wojciech", "Paweł", "Krzysztof", "Michał", "Józef"],
        ["Jan", "Piotr"],
        ["Wróbel", "Kowalski", "Wiśniewski", "Dąbrowski", "Zieliński", "Lewandowski"],
    ),
    "TR": (
        ["Mehmet", "Ahmet", "Çağlar", "Ömer", "Mustafa", "Barış", "Gökhan"],
        [],
        ["Şahin", "Öztürk", "Yılmaz", "Çelik", "Doğan", "Aydın", "Kılıç"],
    ),
    "NG": (
        ["Chinedu", "Oluwaseun", "Emeka", "Ngozi", "Babatunde", "Chukwuma", "Adebayo", "Ifeoma"],
        ["Olu", "Chidi"],
        ["Okafor", "Adeyemi", "Okonkwo", "Balogun", "Abubakar", "Danjuma", "Eze", "Nwosu"],
    ),
}
REGION_WEIGHTS = {"RU": 22, "SY": 22, "IN": 14, "CN": 10, "MX": 12, "DE": 5, "PL": 5, "TR": 5, "NG": 5}

# Groups of equivalent romanisations. Used for aliases and for TRANSLITERATION test variants.
TRANSLITERATIONS = [
    ["Aleksandr", "Alexander", "Alexandr"], ["Dmitri", "Dmitry", "Dmitriy"], ["Sergei", "Sergey"],
    ["Yuri", "Yury", "Iouri"], ["Andrei", "Andrey"], ["Nikolai", "Nikolay"], ["Vasily", "Vasiliy"],
    ["Grigory", "Grigoriy"], ["Anatoly", "Anatoliy"], ["Gennady", "Gennadiy"],
    ["Vorobyov", "Vorobiev"], ["Semenov", "Semyonov"], ["Zaitsev", "Zaytsev"],
    ["Mohammed", "Muhammad", "Mohamed", "Mohammad"], ["Ahmed", "Ahmad"],
    ["Youssef", "Yusuf", "Yousef"], ["Hussein", "Husain", "Hussain"], ["Omar", "Umar"],
    ["Abdullah", "Abdallah"], ["Mustafa", "Mostafa"], ["Hassan", "Hasan"], ["Khalid", "Khaled"],
    ["Qasim", "Kassem"], ["Suleiman", "Sulaiman"], ["Tariq", "Tarek"], ["Rashid", "Rachid"],
    ["Lakshmi", "Laxmi"], ["Chatterjee", "Chaterji"], ["Banerjee", "Banerji"],
    ["Zhou", "Chou"], ["Zhang", "Chang"],
]
TRANSLIT_LOOKUP = {name: group for group in TRANSLITERATIONS for name in group}

HONORIFICS = ["Mr", "Mrs", "Dr", "Shri", "Mr.", "Dr."]

# Names deliberately absent from every pool, used for "unlisted common name" negatives.
UNLISTED_FIRST = ["John", "Emily", "Thomas", "Grace", "Daniel", "Olivia", "Peter", "Hannah"]
UNLISTED_LAST = ["Smith", "Clarke", "Johnson", "Bennett", "Thompson", "Fletcher", "Harrison"]


def strip_accents(text: str) -> str:
    replacements = {"ł": "l", "Ł": "L", "ß": "ss", "ı": "i"}
    text = "".join(replacements.get(ch, ch) for ch in text)
    return "".join(ch for ch in unicodedata.normalize("NFD", text) if unicodedata.category(ch) != "Mn")


def token_key(name: str) -> frozenset[str]:
    """Order-insensitive identity of a name, used to keep primary names unique and label negatives."""
    cleaned = strip_accents(name).lower().replace("-", " ").replace(".", " ")
    return frozenset(cleaned.split())


@dataclass
class Entry:
    entry_id: str
    region: str          # name pool the entry was drawn from (nationality may be blank)
    nationality: str
    first: str
    middle: str | None
    last: str
    list_type: str
    dob: date | None
    aliases: list[str] = field(default_factory=list)

    @property
    def primary_name(self) -> str:
        return " ".join(p for p in (self.first, self.middle, self.last) if p)


def random_dob(rng: random.Random) -> date:
    return date(1940, 1, 1) + timedelta(days=rng.randrange(0, 365 * 55))


def make_aliases(rng: random.Random, first: str, middle: str | None, last: str) -> list[str]:
    candidates: list[str] = []
    for token in (first, last):
        if token in TRANSLIT_LOOKUP:
            others = [v for v in TRANSLIT_LOOKUP[token] if v != token]
            variant = rng.choice(others)
            candidates.append(f"{variant} {last}" if token == first else f"{first} {variant}")
    candidates.append(f"{last.upper()}, {first}")          # list-style "SURNAME, Given"
    if middle:
        candidates.append(f"{first} {last}")             # middle name dropped
    if strip_accents(first + last) != first + last:
        candidates.append(strip_accents(f"{first} {last}"))
    rng.shuffle(candidates)
    return candidates[: rng.randint(0, 3)]


def generate_entries(rng: random.Random, count: int) -> list[Entry]:
    regions = list(REGION_WEIGHTS)
    weights = [REGION_WEIGHTS[r] for r in regions]
    seen: set[frozenset[str]] = set()
    entries: list[Entry] = []
    while len(entries) < count:
        nat = rng.choices(regions, weights)[0]
        firsts, middles, lasts = POOLS[nat]
        first, last = rng.choice(firsts), rng.choice(lasts)
        middle = rng.choice(middles) if middles and rng.random() < 0.4 else None
        candidate = Entry(
            entry_id=f"WL-{len(entries) + 1:05d}",
            region=nat,
            nationality=nat if rng.random() < 0.9 else "",
            first=first, middle=middle, last=last,
            list_type="SANCTIONS" if rng.random() < 0.65 else "PEP",
            dob=random_dob(rng) if rng.random() < 0.9 else None,
        )
        key = token_key(candidate.primary_name)
        if key in seen:
            continue
        seen.add(key)
        candidate.aliases = make_aliases(rng, first, middle, last)
        entries.append(candidate)
    return entries


# --- Evaluation dataset -------------------------------------------------------------------------
def positive_variants(entry: Entry) -> dict[str, str]:
    """All applicable true-match variants for an entry, keyed by variant type."""
    f, m, l = entry.first, entry.middle, entry.last
    variants: dict[str, str] = {
        "SWAPPED_ORDER": f"{l} {f}" + (f" {m}" if m else ""),
        # entry-id based (not hash()) because str hashing is randomised per Python process
        "HONORIFIC": f"{HONORIFICS[int(entry.entry_id[3:]) % len(HONORIFICS)]} {entry.primary_name}",
        "CASE_PUNCTUATION": f"{l.upper()}, {f.lower()}" + (f" {m.lower()}" if m else ""),
        "INITIAL": f"{f[0]}. " + (f"{m} " if m else "") + l,
    }
    if m:
        variants["MISSING_MIDDLE"] = f"{f} {l}"
    if strip_accents(entry.primary_name) != entry.primary_name:
        variants["DIACRITICS"] = strip_accents(entry.primary_name)
    for token in (f, l):
        if token in TRANSLIT_LOOKUP:
            alt = next(v for v in TRANSLIT_LOOKUP[token] if v != token)
            variants["TRANSLITERATION"] = entry.primary_name.replace(token, alt)
            variants["COMBINED"] = f"Mr {l} " + (alt if token == f else f)   # honorific+swap+translit
            break
    return variants


def build_positives(rng: random.Random, entries: list[Entry], per_type: dict[str, int]) -> list[dict]:
    rows: list[dict] = []
    used: set[str] = set()
    shuffled = entries[:]
    rng.shuffle(shuffled)
    for variant_type, wanted in per_type.items():
        found = 0
        for entry in shuffled:
            if found == wanted:
                break
            if entry.entry_id in used:
                continue
            variants = positive_variants(entry)
            if variant_type not in variants:
                continue
            used.add(entry.entry_id)
            found += 1
            rows.append({
                "customerName": variants[variant_type],
                "dob": entry.dob.isoformat() if entry.dob else "",
                "nationality": entry.nationality,
                "expectedEntryId": entry.entry_id,
                "variantType": variant_type,
            })
    return rows


def build_negatives(rng: random.Random, entries: list[Entry], per_type: dict[str, int]) -> list[dict]:
    """Names that are NOT on the list. Near-misses share a token with listed people but are different people."""
    listed = {token_key(e.primary_name) for e in entries}
    listed |= {token_key(a) for e in entries for a in e.aliases}
    # Pairs of tokens that co-occur in any listed name (incl. transliteration variants) - must be avoided.
    co_occurring: set[frozenset[str]] = set()
    for key in listed:
        expanded = set(key)
        for token in key:
            for group in TRANSLITERATIONS:
                if token in {strip_accents(g).lower() for g in group}:
                    expanded |= {strip_accents(g).lower() for g in group}
        co_occurring |= {frozenset((a, b)) for a in expanded for b in expanded if a != b}

    def unlisted(first: str, last: str) -> bool:
        pair = frozenset((strip_accents(first).lower(), strip_accents(last).lower()))
        return pair not in co_occurring and token_key(f"{first} {last}") not in listed

    nats = list(POOLS)
    makers = {
        # Same surname as a listed person, different (same-region) given name.
        "SHARED_SURNAME": lambda: (lambda e: (rng.choice(POOLS[e.region][0]), e.last))(rng.choice(entries)),
        # Same given name as a listed person, surname from a different region.
        "SHARED_FIRST_NAME": lambda: (lambda e: (e.first, rng.choice(POOLS[rng.choice(nats)][2])))(rng.choice(entries)),
        # Given name of one listed person + surname of another listed person.
        "CROSS_ENTRY_TOKENS": lambda: (rng.choice(entries).first, rng.choice(entries).last),
        # A completely unlisted common name.
        "UNLISTED_COMMON_NAME": lambda: (rng.choice(UNLISTED_FIRST), rng.choice(UNLISTED_LAST)),
    }
    rows: list[dict] = []
    seen: set[frozenset[str]] = set()
    for variant_type, wanted in per_type.items():
        found = 0
        while found < wanted:
            if variant_type == "SINGLE_TOKEN":
                name = rng.choice(entries).last if found % 2 == 0 else rng.choice(entries).first
                first, last = name, ""
                ok = token_key(name) not in listed
            else:
                first, last = makers[variant_type]()
                ok = unlisted(first, last)
            name = f"{first} {last}".strip()
            if not ok or token_key(name) in seen:
                continue
            seen.add(token_key(name))
            found += 1
            rows.append({"customerName": name, "dob": random_dob(rng).isoformat(), "nationality": "",
                         "expectedEntryId": "NONE", "variantType": variant_type})
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--entries", type=int, default=2000)
    parser.add_argument("--out", type=Path, default=Path("data"))
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    entries = generate_entries(rng, args.entries)
    positives = build_positives(rng, entries, {
        "SWAPPED_ORDER": 5, "HONORIFIC": 5, "CASE_PUNCTUATION": 4, "INITIAL": 4, "MISSING_MIDDLE": 5,
        "DIACRITICS": 5, "TRANSLITERATION": 8, "COMBINED": 4,
    })
    negatives = build_negatives(rng, entries, {
        "SHARED_SURNAME": 6, "SHARED_FIRST_NAME": 5, "CROSS_ENTRY_TOKENS": 6, "SINGLE_TOKEN": 4,
        "UNLISTED_COMMON_NAME": 4,
    })

    args.out.mkdir(parents=True, exist_ok=True)
    fields = ["entryId", "primaryName", "aliases", "dob", "nationality", "listType"]
    records = [{
        "entryId": e.entry_id, "primaryName": e.primary_name, "aliases": e.aliases,
        "dob": e.dob.isoformat() if e.dob else None, "nationality": e.nationality or None,
        "listType": e.list_type,
    } for e in entries]

    with open(args.out / "watchlist.csv", "w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=fields)
        writer.writeheader()
        for r in records:
            writer.writerow({**r, "aliases": "|".join(r["aliases"]), "dob": r["dob"] or "",
                             "nationality": r["nationality"] or ""})
    with open(args.out / "watchlist.json", "w", encoding="utf-8") as fh:
        json.dump(records, fh, ensure_ascii=False, indent=1)
    with open(args.out / "evaluation.csv", "w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=["caseId", "customerName", "dob", "nationality",
                                                "expectedEntryId", "variantType"])
        writer.writeheader()
        for i, row in enumerate(positives + negatives, start=1):
            writer.writerow({"caseId": f"EVAL-{i:03d}", **row})

    print(f"entries={len(entries)} positives={len(positives)} negatives={len(negatives)} -> {args.out}/")


if __name__ == "__main__":
    main()
