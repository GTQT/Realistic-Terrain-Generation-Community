# 第三方代码与许可

本仓库以 **GPLv3** 分发（见 `LICENSE`）。以下第三方作品以 **MIT** 许可并入，
按 MIT 的要求在此保留原始版权声明与许可全文。

## WorleyCaves

- 来源：WorleyCaves 1.5.2（Minecraft 1.12.2），`https://github.com/SuperFluke/WorleyCaves`
- 作者：SuperFluke、booleanbyte
- 并入位置：`src/main/java/rtg/world/gen/worley/`
  - `WorleyNoise.java` ← `fluke.worleycaves.util.WorleyUtil`
  - `WorleyCavesGenerator.java` ← `fluke.worleycaves.world.WorleyCaveGenerator`
  - `WorleyCaveParams.java` ← `fluke.worleycaves.config.Configs.ConfigCaveGen` 的字段
- 说明：算法逐行保留；改动仅限包名、噪声种子注入、地表材质来源、数据结构与缓存策略，
  详见 `docs/worley-caves-merge-feasibility.md`。

```
The MIT License (MIT)

Copyright (c) 2018 SuperFluke, booleanbyte

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## FastNoise

- 来源：FastNoise（C++/Java 噪声库），`https://github.com/Auburns/FastNoise`
- 作者：Jordan Peck
- 并入位置：`src/main/java/rtg/world/gen/worley/WorleyWarpNoise.java`
  （只包含其 2D Perlin + `FastFloor` 的子集；`CELL_3D` 梯度表随 WorleyCaves 的
  `WorleyUtil` 一并进入 `WorleyNoise.java`）
- 说明：未整体引入 FastNoise。其 `GradientPerturb*` 依赖 `javax.vecmath`，
  而 1.12.2 运行环境没有该包，整包引入会留下 `NoClassDefFoundError` 隐患。

```
The MIT License (MIT)

Copyright (c) 2017 Jordan Peck

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
