# ♣️ Kronos Renderer

Kronos *is for now* a terrain renderer replacement structured upon and for 
old versions of Minecraft, it has many inspirations from Sodium and previous
work, but it benefits greatly from being targeted and optimized for old versions
which have some different architecture and compatibility concerns.

## ⚠️ Compatibility

The mod is still in development and far from well tested, for reporting bugs, compatibility
problems, vanilla deviations and general concerns, please report a issue in the repository.

## ☢️ Optimizations/Implementation

The main premise of the mod is that the terrain loop with a more optimized totally different
rendering path that doesn't build upon legacy brain-fart code, between all the characteristics,
these are the main ones:

- Uses a region draw system that minimizes drawing overhead by setting up work in bigger batches
  and uses more modern OpenGL rendering techniques.
- Add more culling in top of vanilla techniques to minimize drawn chunks, as distance culling, 
  occlusion culling from a graph search and ray-culling, and lastly back-face culling for blocks
  back-facing.
- Optimized and rewritten meshing pipeline that in many circumstances can get as much as >x8 faster.
- Much more compact vertex size (16-bytes), that combined all the culling methods can reduce V-RAM
  usage >70%.

## 🌐 Versions

For now is only ported for BTA Adventure and 1.6.4 Forge, maybe in the future I maintain as a 
possibility porting the mod to versions in the range of the betas and <1.6.4 but not forward
as there is already better maintained optimizations for such versions.

## 📠 License

For now is ARR, but it's only for convenience, and for sure in a future I want to use a OSS
licence or more permissive one to share the work, any concerns about license can also be
discussed with a issue in the repository.


