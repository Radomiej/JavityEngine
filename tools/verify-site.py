#!/usr/bin/env python3
"""Validate the self-contained GitHub Pages artifact without network dependencies."""
from html.parser import HTMLParser
from pathlib import Path
import sys
from urllib.parse import unquote, urlsplit


class Document(HTMLParser):
    def __init__(self, path):
        super().__init__(convert_charrefs=True)
        self.path, self.ids, self.references, self.errors = path, set(), [], []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if "id" in attrs:
            if attrs["id"] in self.ids:
                self.errors.append(f"duplicate id: {attrs['id']}")
            self.ids.add(attrs["id"])
        for name in ("href", "src"):
            if attrs.get(name):
                self.references.append(attrs[name])
        if tag == "img" and "alt" not in attrs:
            self.errors.append("image without alt attribute")


def verify(directory):
    root = directory.resolve()
    documents = {}
    errors = []
    for path in root.rglob("*.html"):
        document = Document(path)
        document.feed(path.read_text(encoding="utf-8"))
        documents[path] = document
        errors.extend(f"{path.relative_to(root)}: {e}" for e in document.errors)
    if root / "index.html" not in documents:
        errors.append("missing index.html")
    for path, document in documents.items():
        for reference in document.references:
            url = urlsplit(reference)
            if url.scheme or url.netloc:
                if url.scheme not in ("https", "mailto", "data"):
                    errors.append(f"unsupported external URL: {reference}")
                continue
            if url.path.startswith("/"):
                errors.append(f"root-relative URL breaks repository subpath: {reference}")
                continue
            target = (path.parent / unquote(url.path)).resolve() if url.path else path
            if not target.is_relative_to(root):
                errors.append(f"reference escapes site: {reference}")
                continue
            if target.is_dir():
                target /= "index.html"
            if not target.is_file():
                errors.append(f"missing file: {reference}")
            elif url.fragment and target in documents and unquote(url.fragment) not in documents[target].ids:
                errors.append(f"missing fragment: {reference}")
    if errors:
        raise SystemExit("Site validation failed:\n" + "\n".join(errors))
    print(f"Site validated: {len(documents)} HTML document(s), local files and fragments resolve.")


if __name__ == "__main__":
    verify(Path(sys.argv[1] if len(sys.argv) > 1 else "site"))
