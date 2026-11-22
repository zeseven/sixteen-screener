# Tag icons

Source of the icons a user tag can have. The icon ID stored in a tag is the SVG file name without the extension
(`chess-king`, `piggy-bank`).

- 28 icons from [Bootstrap Icons](https://icons.getbootstrap.com/) (MIT, `LICENSE-bootstrap-icons.txt`)
- `chess-king`, `chess-queen`, `poker-chip` and `building-factory-2` from [Tabler Icons](https://tabler.io/icons)
  (MIT, `LICENSE-tabler-icons.txt`)

`preview.html` shows the whole set in light and dark colors.

The app uses them as vector drawables `app/src/main/res/drawable/ic_tag_<id>.xml` (dashes become underscores),
generated from the SVGs of this folder. After adding or changing an SVG, regenerate them from the `frontend` folder and
add the icon to `TagIcons` in the app:

```bash
uv run tag-icons/convert.py
```
