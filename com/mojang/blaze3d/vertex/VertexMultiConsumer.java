package com.mojang.blaze3d.vertex;

import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VertexMultiConsumer {
    public static VertexConsumer create() {
        throw new IllegalArgumentException();
    }

    public static VertexConsumer create(VertexConsumer p_167062_) {
        return p_167062_;
    }

    public static VertexConsumer create(VertexConsumer p_86169_, VertexConsumer p_86170_) {
        return new VertexMultiConsumer.Double(p_86169_, p_86170_);
    }

    public static VertexConsumer create(VertexConsumer... p_167064_) {
        return new VertexMultiConsumer.Multiple(p_167064_);
    }

    @OnlyIn(Dist.CLIENT)
    static class Double implements VertexConsumer {
        private final VertexConsumer first;
        private final VertexConsumer second;

        public Double(VertexConsumer p_86174_, VertexConsumer p_86175_) {
            if (p_86174_ == p_86175_) {
                throw new IllegalArgumentException("Duplicate delegates");
            } else {
                this.first = p_86174_;
                this.second = p_86175_;
            }
        }

        @Override
        public VertexConsumer addVertex(float p_350863_, float p_350291_, float p_350475_) {
            this.first.addVertex(p_350863_, p_350291_, p_350475_);
            this.second.addVertex(p_350863_, p_350291_, p_350475_);
            return this;
        }

        @Override
        public VertexConsumer setColor(int p_350709_, int p_350378_, int p_350552_, int p_350453_) {
            this.first.setColor(p_350709_, p_350378_, p_350552_, p_350453_);
            this.second.setColor(p_350709_, p_350378_, p_350552_, p_350453_);
            return this;
        }

        @Override
        public VertexConsumer setUv(float p_350403_, float p_350914_) {
            this.first.setUv(p_350403_, p_350914_);
            this.second.setUv(p_350403_, p_350914_);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int p_350402_, int p_351027_) {
            this.first.setUv1(p_350402_, p_351027_);
            this.second.setUv1(p_350402_, p_351027_);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int p_350512_, int p_350829_) {
            this.first.setUv2(p_350512_, p_350829_);
            this.second.setUv2(p_350512_, p_350829_);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float p_350811_, float p_350770_, float p_350296_) {
            this.first.setNormal(p_350811_, p_350770_, p_350296_);
            this.second.setNormal(p_350811_, p_350770_, p_350296_);
            return this;
        }

        @Override
        public void addVertex(
            float p_350971_,
            float p_350483_,
            float p_351043_,
            int p_350835_,
            float p_350590_,
            float p_350299_,
            int p_350687_,
            int p_350641_,
            float p_350742_,
            float p_350591_,
            float p_350324_
        ) {
            this.first.addVertex(p_350971_, p_350483_, p_351043_, p_350835_, p_350590_, p_350299_, p_350687_, p_350641_, p_350742_, p_350591_, p_350324_);
            this.second.addVertex(p_350971_, p_350483_, p_351043_, p_350835_, p_350590_, p_350299_, p_350687_, p_350641_, p_350742_, p_350591_, p_350324_);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record Multiple(VertexConsumer[] delegates) implements VertexConsumer {
        Multiple(VertexConsumer[] delegates) {
            for (int i = 0; i < delegates.length; i++) {
                for (int j = i + 1; j < delegates.length; j++) {
                    if (delegates[i] == delegates[j]) {
                        throw new IllegalArgumentException("Duplicate delegates");
                    }
                }
            }

            this.delegates = delegates;
        }

        private void forEach(Consumer<VertexConsumer> p_167145_) {
            for (VertexConsumer vertexconsumer : this.delegates) {
                p_167145_.accept(vertexconsumer);
            }
        }

        @Override
        public VertexConsumer addVertex(float p_350626_, float p_351046_, float p_351003_) {
            this.forEach(p_349771_ -> p_349771_.addVertex(p_350626_, p_351046_, p_351003_));
            return this;
        }

        @Override
        public VertexConsumer setColor(int p_167130_, int p_167131_, int p_167132_, int p_167133_) {
            this.forEach(p_349757_ -> p_349757_.setColor(p_167130_, p_167131_, p_167132_, p_167133_));
            return this;
        }

        @Override
        public VertexConsumer setUv(float p_167084_, float p_167085_) {
            this.forEach(p_349767_ -> p_349767_.setUv(p_167084_, p_167085_));
            return this;
        }

        @Override
        public VertexConsumer setUv1(int p_350622_, int p_350367_) {
            this.forEach(p_349752_ -> p_349752_.setUv1(p_350622_, p_350367_));
            return this;
        }

        @Override
        public VertexConsumer setUv2(int p_350498_, int p_350436_) {
            this.forEach(p_349764_ -> p_349764_.setUv2(p_350498_, p_350436_));
            return this;
        }

        @Override
        public VertexConsumer setNormal(float p_167147_, float p_167148_, float p_167149_) {
            this.forEach(p_349761_ -> p_349761_.setNormal(p_167147_, p_167148_, p_167149_));
            return this;
        }

        @Override
        public void addVertex(
            float p_350950_,
            float p_350670_,
            float p_350366_,
            int p_350619_,
            float p_350871_,
            float p_350850_,
            int p_350499_,
            int p_350318_,
            float p_350404_,
            float p_350988_,
            float p_350991_
        ) {
            this.forEach(
                p_349749_ -> p_349749_.addVertex(
                        p_350950_, p_350670_, p_350366_, p_350619_, p_350871_, p_350850_, p_350499_, p_350318_, p_350404_, p_350988_, p_350991_
                    )
            );
        }
    }
}
