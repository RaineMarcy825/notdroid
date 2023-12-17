package com.edlplan.framework.support.osb;

import com.edlplan.edlosbsupport.OsuStoryboard;
import com.edlplan.edlosbsupport.OsuStoryboardLayer;
import com.edlplan.edlosbsupport.elements.IStoryboardElement;
import com.edlplan.edlosbsupport.elements.StoryboardAnimationSprite;
import com.edlplan.edlosbsupport.parser.OsbFileParser;
import com.edlplan.edlosbsupport.player.OsbPlayer;
import com.edlplan.framework.math.Vec2;
import com.edlplan.framework.support.ProxySprite;
import com.edlplan.framework.support.SupportSprite;
import com.edlplan.framework.support.graphics.BaseCanvas;
import com.edlplan.framework.support.graphics.texture.TexturePool;
import com.edlplan.framework.support.util.Tracker;
import com.edlplan.framework.utils.functionality.SmartIterator;

import java.io.File;
import java.util.HashMap;

import ru.nsu.ccfit.zuev.osu.helper.FileUtils;

import static com.edlplan.edlosbsupport.elements.StoryboardSprite.*;

public class StoryboardSprite extends SupportSprite {

    OsbContext context = new OsbContext();
    OsuStoryboard storyboard;
    OsbPlayer osbPlayer;
    String loadedOsu;
    private double time;

    private final Vec2 startOffset = new Vec2();


    public StoryboardSprite(float width, float height) {
        super(width, height);
    }

    private static HashMap<String, Integer> countTextureUsedTimes(OsuStoryboard storyboard) {
        HashMap<String, Integer> textures = new HashMap<>();
        Integer tmp;
        String tmps;
        for (OsuStoryboardLayer layer : storyboard.layers) {
            if (layer != null) {
                for (IStoryboardElement element : layer.elements) {
                    if (element instanceof StoryboardAnimationSprite) {
                        StoryboardAnimationSprite as = (StoryboardAnimationSprite) element;
                        for (int i = 0; i < as.frameCount; i++) {
                            if ((tmp = textures.get(tmps = as.buildPath(i))) == null) {
                                textures.put(tmps, 1);
                                continue;
                            }
                            textures.put(tmps, tmp + 1);
                        }
                    } else if (element instanceof com.edlplan.edlosbsupport.elements.StoryboardSprite) {
                        if ((tmp = textures.get(tmps = ((com.edlplan.edlosbsupport.elements.StoryboardSprite) element).spriteFilename)) == null) {
                            textures.put(tmps, 1);
                            continue;
                        }
                        textures.put(tmps, tmp + 1);
                    }
                }
            }
        }
        return textures;
    }

    public TexturePool getLoadedPool() {
        return context.texturePool;
    }


    public void updateTime(double time) {
        if (Math.abs(this.time - time) > 10) {
            this.time = time;
            if (osbPlayer != null) {
                osbPlayer.update(time);
            }
        }
    }

    public boolean isStoryboardAvailable() {
        return storyboard != null;
    }


    @Override
    protected void onSupportDraw(BaseCanvas canvas) {
        super.onSupportDraw(canvas);

        if (storyboard == null) {
            return;
        }

        canvas.getBlendSetting().save();
        canvas.save();
        float scale = Math.max(640 / canvas.getWidth(), 480 / canvas.getHeight());
        startOffset.set(canvas.getWidth() / 2, canvas.getHeight() / 2);
        startOffset.minus(640 * 0.5f / scale, 480 * 0.5f / scale);

        canvas.translate(startOffset.x, startOffset.y).expendAxis(scale);

        if (context.engines != null) {
            int i = 0;
            int length = context.engines.length;
            while (i < length) {
                var engine = context.engines[i];
                if (engine != null && engine.getLayer() != Layer.Overlay) {
                    engine.draw(canvas);
                }
                i++;
            }
        }

        canvas.restore();
        canvas.getBlendSetting().restore();
    }

    private File findOsb(String osuFile) {
        File dir = new File(osuFile);
        dir = dir.getParentFile();
        File[] fs = FileUtils.listFiles(dir, ".osb");
        if (fs.length > 0) {
            return fs[0];
        } else {
            return null;
        }
    }

    private void loadOsb(String osuFile) {
        File file = findOsb(osuFile);
        if (file == null) {
            return;
        }

        OsbFileParser parser = new OsbFileParser(
                file,
                null);

        Tracker.createTmpNode("ParseOsb").wrap(() -> {
            try {
                parser.parse();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).then(System.out::println);

        storyboard = parser.getBaseParser().getStoryboard();
    }

    private void loadOsu(String osuFile) {
        OsbFileParser parser = new OsbFileParser(new File(osuFile), null);
        Tracker.createTmpNode("ParseOsu").wrap(() -> {
            try {
                parser.parse();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).then(System.out::println);

        OsuStoryboard osustoryboard = parser.getBaseParser().getStoryboard();

        if (storyboard == null) {
            boolean empty = true;
            for (OsuStoryboardLayer layer : osustoryboard.layers) {
                if (layer != null) {
                    empty = false;
                    break;
                }
            }
            if (empty) {
                return;
            }
            storyboard = osustoryboard;
        } else {
            storyboard.appendStoryboard(osustoryboard);
        }
    }

    private void loadFromCache() {

        context.engines = new LayerRenderEngine[Layer.values().length];
        for (int i = 0; i < context.engines.length; i++) {
            context.engines[i] = new LayerRenderEngine(Layer.values()[i]);
        }

        if (storyboard == null) {
            return;
        }

        osbPlayer = new OsbPlayer(s -> {
            if (s.getClass() == com.edlplan.edlosbsupport.elements.StoryboardSprite.class) {
                return new EGFStoryboardSprite(context);
            } else {
                return new EGFStoryboardAnimationSprite(context);
            }
        });

        Tracker.createTmpNode("LoadPlayer").wrap(() -> osbPlayer.loadStoryboard(storyboard)).then(System.out::println);
    }

    public void loadStoryboard(String osuFile) {
        System.out.println(this + " load storyboard from " + osuFile);
        if (osuFile.equals(loadedOsu)) {
            System.out.println("load storyboard from cache");
            loadFromCache();
            return;
        }
        loadedOsu = osuFile;

        releaseStoryboard();

        loadedOsu = osuFile;

        File osu = new File(osuFile);
        File dir = osu.getParentFile();
        TexturePool pool = new TexturePool(dir);

        context.texturePool = pool;
        context.engines = new LayerRenderEngine[Layer.values().length];
        for (int i = 0; i < context.engines.length; i++) {
            context.engines[i] = new LayerRenderEngine(Layer.values()[i]);
        }

        loadOsb(osuFile);
        loadOsu(osuFile);

        if (storyboard == null) {
            return;
        }

        Tracker.createTmpNode("PackTextures").wrap(() -> {
            //Set<String> all = new HashSet<>();// = storyboard.getAllNeededTextures();
            HashMap<String, Integer> counted = countTextureUsedTimes(storyboard);

            SmartIterator<String> allToPack = SmartIterator.wrap(counted.keySet().iterator())
                    .applyFilter(s -> counted.get(s) >= 15);
            pool.packAll(allToPack, null);

            allToPack = SmartIterator.wrap(counted.keySet().iterator())
                    .applyFilter(s -> counted.get(s) < 15);
            while (allToPack.hasNext()) {
                pool.add(allToPack.next());
            }
        }).then(System.out::println);


        osbPlayer = new OsbPlayer(s -> {
            if (s.getClass() == com.edlplan.edlosbsupport.elements.StoryboardSprite.class) {
                return new EGFStoryboardSprite(context);
            } else {
                return new EGFStoryboardAnimationSprite(context);
            }
        });

        Tracker.createTmpNode("LoadPlayer").wrap(() -> osbPlayer.loadStoryboard(storyboard)).then(System.out::println);

    }

    public void releaseStoryboard() {
        if (context.texturePool != null) {
            context.texturePool.clear();
            context.texturePool = null;
        }
        if (storyboard != null) {
            storyboard.clear();
        }
        if (osbPlayer != null) {
            osbPlayer = null;
        }
        loadedOsu = null;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        releaseStoryboard();
    }
}
