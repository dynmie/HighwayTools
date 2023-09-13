//package me.dynmie.highway.highwaytools.blueprint.impl;
//
//import me.dynmie.highway.modules.HighwayTools;
//import me.dynmie.highway.highwaytools.blueprint.BlueprintProvider;
//import meteordevelopment.meteorclient.utils.misc.MBlockPos;
//import net.minecraft.client.MinecraftClient;
//import org.jetbrains.annotations.NotNull;
//
//public class DiagonalBlueprintProvider implements BlueprintProvider {
//    private final MinecraftClient mc = MinecraftClient.getInstance();
//
//    private final MBlockPos pos = new MBlockPos();
//    private final MBlockPos pos2 = new MBlockPos();
//
//    private final HighwayTools tools;
//
//    public DiagonalBlueprintProvider(HighwayTools tools) {
//        this.tools = tools;
//    }
//
//    @Override
//    public @NotNull MBPIterator getFront() {
//        pos.set(tools.getMc().player).offset(tools.getDir().rotateLeft()).offset(tools.getLeftDir(), tools.getWidthLeftOffset() - 1);
//
//        return new MBPIterator() {
//            private int i, w, y;
//            private int pi, pw, py;
//
//            @Override
//            public boolean hasNext() {
//                return i < 2 && w < tools.getWidth().get() && y < tools.getHeight().get();
//            }
//
//            @Override
//            public MBlockPos next() {
//                pos2.set(pos).offset(tools.getRightDir(), w).add(0, y++, 0);
//
//                if (y >= tools.getHeight().get()) {
//                    y = 0;
//                    w++;
//
//                    if (w >= (i == 0 ? tools.getWidth().get() - 1 : tools.getWidth().get())) {
//                        w = 0;
//                        i++;
//
//                        pos.set(tools.getMc().player).offset(tools.getDir()).offset(tools.getLeftDir(), tools.getWidthLeftOffset());
//                    }
//                }
//
//                return pos2;
//            }
//
//            private void initPos() {
//                if (i == 0)
//                    pos.set(mc.player).offset(tools.getDir().rotateLeft()).offset(tools.getLeftDir(), tools.getWidthLeftOffset() - 1);
//                else pos.set(mc.player).offset(tools.getDir()).offset(tools.getLeftDir(), tools.getWidthLeftOffset());
//            }
//
//            @Override
//            public void save() {
//                pi = i;
//                pw = w;
//                py = y;
//                i = w = y = 0;
//
//                initPos();
//            }
//
//            @Override
//            public void restore() {
//                i = pi;
//                w = pw;
//                y = py;
//
//                initPos();
//            }
//        };
//    }
//
//    @Override
//    public @NotNull MBPIterator getFloor() {
//        pos.set(mc.player).add(0, -1, 0).offset(tools.getDir().rotateLeft()).offset(tools.getLeftDir(), tools.getWidthLeftOffset() - 1);
//
//        return new MBPIterator() {
//            // TODO EXTEND FLOOR AND DONT REMOVE BLOCK FROM UNDER PLAYER
//            private int i, w;
//            private int pi, pw;
//
//            @Override
//            public boolean hasNext() {
//                return i < 2 && w < tools.getWidth().get();
//            }
//
//            @Override
//            public MBlockPos next() {
//                pos2.set(pos).offset(tools.getRightDir(), w++);
//
//                if (w >= (i == 0 ? tools.getWidth().get() - 1 : tools.getWidth().get())) {
//                    w = 0;
//                    i++;
//
//                    pos.set(mc.player).add(0, -1, 0).offset(tools.getDir()).offset(tools.getLeftDir(), tools.getWidthLeftOffset());
//                }
//
//                return pos2;
//            }
//
//            private void initPos() {
//                if (i == 0)
//                    pos.set(mc.player).add(0, -1, 0).offset(tools.getDir().rotateLeft()).offset(tools.getLeftDir(), tools.getWidthLeftOffset() - 1);
//                else
//                    pos.set(mc.player).add(0, -1, 0).offset(tools.getDir()).offset(tools.getLeftDir(), tools.getWidthLeftOffset());
//            }
//
//            @Override
//            public void save() {
//                pi = i;
//                pw = w;
//                i = w = 0;
//
//                initPos();
//            }
//
//            @Override
//            public void restore() {
//                i = pi;
//                w = pw;
//
//                initPos();
//            }
//        };
//    }
//
//    @Override
//    public MBPIterator getRailings(boolean mine) {
//        boolean mineAll = mine && tools.getMineAboveRailings().get();
//        pos.set(mc.player).offset(tools.getDir().rotateLeft()).offset(tools.getLeftDir(), tools.getWidthLeftOffset());
//
//        return new MBPIterator() {
//            private int i, y;
//            private int pi, py;
//
//            @Override
//            public boolean hasNext() {
//                return i < 2 && y < (mineAll ? tools.getHeight().get() : 1);
//            }
//
//            @Override
//            public MBlockPos next() {
//                pos2.set(pos).add(0, y++, 0);
//
//                if (y >= (mineAll ? tools.getHeight().get() : 1)) {
//                    y = 0;
//                    i++;
//
//                    pos.set(mc.player).offset(tools.getDir().rotateRight()).offset(tools.getRightDir(), tools.getWidthRightOffset());
//                }
//
//                return pos2;
//            }
//
//            private void initPos() {
//                if (i == 0)
//                    pos.set(mc.player).offset(tools.getDir().rotateLeft()).offset(tools.getLeftDir(), tools.getWidthLeftOffset());
//                else
//                    pos.set(mc.player).offset(tools.getDir().rotateRight()).offset(tools.getRightDir(), tools.getWidthRightOffset());
//            }
//
//            @Override
//            public void save() {
//                pi = i;
//                py = y;
//                i = y = 0;
//
//                initPos();
//            }
//
//            @Override
//            public void restore() {
//                i = pi;
//                y = py;
//
//                initPos();
//            }
//        };
//    }
//
//    @Override
//    public @NotNull MBPIterator getLiquids() {
//        boolean m = tools.getRailings().get() && tools.getMineAboveRailings().get();
//        pos.set(mc.player).offset(tools.getDir()).offset(tools.getDir().rotateLeft()).offset(tools.getLeftDir(), tools.getWidthLeftOffset());
//
//        return new MBPIterator() {
//            private int i, w, y;
//            private int pi, pw, py;
//
//            private int getWidth() {
//                return tools.getWidth().get() + (i == 0 ? 1 : 0) + (m && i == 1 ? 2 : 0);
//            }
//
//            @Override
//            public boolean hasNext() {
//                if (m && i == 1 && y == tools.getHeight().get() && w == getWidth() - 1) return false;
//                return i < 2 && w < getWidth() && y < tools.getHeight().get() + 1;
//            }
//
//            private void updateW() {
//                w++;
//
//                if (w >= getWidth()) {
//                    w = 0;
//                    i++;
//
//                    pos.set(mc.player).offset(tools.getDir(), 2).offset(tools.getLeftDir(), tools.getWidthLeftOffset() + (m ? 1 : 0));
//                }
//            }
//
//            @Override
//            public MBlockPos next() {
//                if (i == (m ? 1 : 0) && y == tools.getHeight().get() && (w == 0 || w == getWidth() - 1)) {
//                    y = 0;
//                    updateW();
//                }
//
//                pos2.set(pos).offset(tools.getRightDir(), w).add(0, y++, 0);
//
//                if (y >= tools.getHeight().get() + 1) {
//                    y = 0;
//                    updateW();
//                }
//
//                return pos2;
//            }
//
//            private void initPos() {
//                if (i == 0)
//                    pos.set(mc.player).offset(tools.getDir()).offset(tools.getDir().rotateLeft()).offset(tools.getLeftDir(), tools.getWidthLeftOffset());
//                else
//                    pos.set(mc.player).offset(tools.getDir(), 2).offset(tools.getLeftDir(), tools.getWidthLeftOffset() + (m ? 1 : 0));
//            }
//
//            @Override
//            public void save() {
//                pi = i;
//                pw = w;
//                py = y;
//                i = w = y = 0;
//
//                initPos();
//            }
//
//            @Override
//            public void restore() {
//                i = pi;
//                w = pw;
//                y = py;
//
//                initPos();
//            }
//        };
//    }
//
//}
