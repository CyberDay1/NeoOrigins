package net.minecraft.network.codec;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import io.netty.buffer.ByteBuf;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {
    static <B, V> StreamCodec<B, V> of(final StreamEncoder<B, V> p_320243_, final StreamDecoder<B, V> p_320197_) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B p_319945_) {
                return p_320197_.decode(p_319945_);
            }

            @Override
            public void encode(B p_320538_, V p_320754_) {
                p_320243_.encode(p_320538_, p_320754_);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(final StreamMemberEncoder<B, V> p_320316_, final StreamDecoder<B, V> p_319991_) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B p_320797_) {
                return p_319991_.decode(p_320797_);
            }

            @Override
            public void encode(B p_319939_, V p_320568_) {
                p_320316_.encode(p_320568_, p_319939_);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(final V p_320438_) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B p_320572_) {
                return p_320438_;
            }

            @Override
            public void encode(B p_320044_, V p_320328_) {
                if (!p_320328_.equals(p_320438_)) {
                    throw new IllegalStateException("Can't encode '" + p_320328_ + "', expected '" + p_320438_ + "'");
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(StreamCodec.CodecOperation<B, V, O> p_320531_) {
        return p_320531_.apply(this);
    }

    default <O> StreamCodec<B, O> map(final Function<? super V, ? extends O> p_320812_, final Function<? super O, ? extends V> p_320191_) {
        return new StreamCodec<B, O>() {
            @Override
            public O decode(B p_320534_) {
                return (O)p_320812_.apply(StreamCodec.this.decode(p_320534_));
            }

            @Override
            public void encode(B p_319798_, O p_320273_) {
                StreamCodec.this.encode(p_319798_, (V)p_320191_.apply(p_320273_));
            }
        };
    }

    default <O extends ByteBuf> StreamCodec<O, V> mapStream(final Function<O, ? extends B> p_320784_) {
        return new StreamCodec<O, V>() {
            public V decode(O p_319818_) {
                B b = (B)p_320784_.apply(p_319818_);
                return StreamCodec.this.decode(b);
            }

            public void encode(O p_319973_, V p_319843_) {
                B b = (B)p_320784_.apply(p_319973_);
                StreamCodec.this.encode(b, p_319843_);
            }
        };
    }

    default <U> StreamCodec<B, U> dispatch(
        final Function<? super U, ? extends V> p_320474_, final Function<? super V, ? extends StreamCodec<? super B, ? extends U>> p_320190_
    ) {
        return new StreamCodec<B, U>() {
            @Override
            public U decode(B p_320094_) {
                V v = StreamCodec.this.decode(p_320094_);
                StreamCodec<? super B, ? extends U> streamcodec = (StreamCodec<? super B, ? extends U>)p_320190_.apply(v);
                return (U)streamcodec.decode(p_320094_);
            }

            @Override
            public void encode(B p_320767_, U p_320010_) {
                V v = (V)p_320474_.apply(p_320010_);
                StreamCodec<B, U> streamcodec = (StreamCodec<B, U>)p_320190_.apply(v);
                StreamCodec.this.encode(p_320767_, v);
                streamcodec.encode(p_320767_, p_320010_);
            }
        };
    }

    static <B, C, T1> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> p_320179_, final Function<C, T1> p_320085_, final Function<T1, C> p_320672_) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B p_320924_) {
                T1 t1 = p_320179_.decode(p_320924_);
                return p_320672_.apply(t1);
            }

            @Override
            public void encode(B p_320798_, C p_320749_) {
                p_320179_.encode(p_320798_, p_320085_.apply(p_320749_));
            }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> p_320642_,
        final Function<C, T1> p_320284_,
        final StreamCodec<? super B, T2> p_320068_,
        final Function<C, T2> p_319959_,
        final BiFunction<T1, T2, C> p_320761_
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B p_320168_) {
                T1 t1 = p_320642_.decode(p_320168_);
                T2 t2 = p_320068_.decode(p_320168_);
                return p_320761_.apply(t1, t2);
            }

            @Override
            public void encode(B p_320592_, C p_320163_) {
                p_320642_.encode(p_320592_, p_320284_.apply(p_320163_));
                p_320068_.encode(p_320592_, p_319959_.apply(p_320163_));
            }
        };
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> p_320928_,
        final Function<C, T1> p_320123_,
        final StreamCodec<? super B, T2> p_319815_,
        final Function<C, T2> p_319965_,
        final StreamCodec<? super B, T3> p_319834_,
        final Function<C, T3> p_320645_,
        final Function3<T1, T2, T3, C> p_320386_
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B p_320842_) {
                T1 t1 = p_320928_.decode(p_320842_);
                T2 t2 = p_319815_.decode(p_320842_);
                T3 t3 = p_319834_.decode(p_320842_);
                return p_320386_.apply(t1, t2, t3);
            }

            @Override
            public void encode(B p_320737_, C p_320439_) {
                p_320928_.encode(p_320737_, p_320123_.apply(p_320439_));
                p_319815_.encode(p_320737_, p_319965_.apply(p_320439_));
                p_319834_.encode(p_320737_, p_320645_.apply(p_320439_));
            }
        };
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> p_323726_,
        final Function<C, T1> p_324414_,
        final StreamCodec<? super B, T2> p_323659_,
        final Function<C, T2> p_323932_,
        final StreamCodec<? super B, T3> p_323964_,
        final Function<C, T3> p_324282_,
        final StreamCodec<? super B, T4> p_323671_,
        final Function<C, T4> p_324254_,
        final Function4<T1, T2, T3, T4, C> p_324090_
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B p_323859_) {
                T1 t1 = p_323726_.decode(p_323859_);
                T2 t2 = p_323659_.decode(p_323859_);
                T3 t3 = p_323964_.decode(p_323859_);
                T4 t4 = p_323671_.decode(p_323859_);
                return p_324090_.apply(t1, t2, t3, t4);
            }

            @Override
            public void encode(B p_323667_, C p_323469_) {
                p_323726_.encode(p_323667_, p_324414_.apply(p_323469_));
                p_323659_.encode(p_323667_, p_323932_.apply(p_323469_));
                p_323964_.encode(p_323667_, p_324282_.apply(p_323469_));
                p_323671_.encode(p_323667_, p_324254_.apply(p_323469_));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> p_324413_,
        final Function<C, T1> p_323568_,
        final StreamCodec<? super B, T2> p_323835_,
        final Function<C, T2> p_323513_,
        final StreamCodec<? super B, T3> p_324112_,
        final Function<C, T3> p_323815_,
        final StreamCodec<? super B, T4> p_323483_,
        final Function<C, T4> p_323725_,
        final StreamCodec<? super B, T5> p_324486_,
        final Function<C, T5> p_323518_,
        final Function5<T1, T2, T3, T4, T5, C> p_324480_
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B p_324610_) {
                T1 t1 = p_324413_.decode(p_324610_);
                T2 t2 = p_323835_.decode(p_324610_);
                T3 t3 = p_324112_.decode(p_324610_);
                T4 t4 = p_323483_.decode(p_324610_);
                T5 t5 = p_324486_.decode(p_324610_);
                return p_324480_.apply(t1, t2, t3, t4, t5);
            }

            @Override
            public void encode(B p_323786_, C p_323619_) {
                p_324413_.encode(p_323786_, p_323568_.apply(p_323619_));
                p_323835_.encode(p_323786_, p_323513_.apply(p_323619_));
                p_324112_.encode(p_323786_, p_323815_.apply(p_323619_));
                p_323483_.encode(p_323786_, p_323725_.apply(p_323619_));
                p_324486_.encode(p_323786_, p_323518_.apply(p_323619_));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> p_331822_,
        final Function<C, T1> p_330864_,
        final StreamCodec<? super B, T2> p_331390_,
        final Function<C, T2> p_331203_,
        final StreamCodec<? super B, T3> p_331499_,
        final Function<C, T3> p_330294_,
        final StreamCodec<? super B, T4> p_331169_,
        final Function<C, T4> p_331830_,
        final StreamCodec<? super B, T5> p_331057_,
        final Function<C, T5> p_331593_,
        final StreamCodec<? super B, T6> p_331117_,
        final Function<C, T6> p_331904_,
        final Function6<T1, T2, T3, T4, T5, T6, C> p_331335_
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B p_330310_) {
                T1 t1 = p_331822_.decode(p_330310_);
                T2 t2 = p_331390_.decode(p_330310_);
                T3 t3 = p_331499_.decode(p_330310_);
                T4 t4 = p_331169_.decode(p_330310_);
                T5 t5 = p_331057_.decode(p_330310_);
                T6 t6 = p_331117_.decode(p_330310_);
                return p_331335_.apply(t1, t2, t3, t4, t5, t6);
            }

            @Override
            public void encode(B p_332052_, C p_331912_) {
                p_331822_.encode(p_332052_, p_330864_.apply(p_331912_));
                p_331390_.encode(p_332052_, p_331203_.apply(p_331912_));
                p_331499_.encode(p_332052_, p_330294_.apply(p_331912_));
                p_331169_.encode(p_332052_, p_331830_.apply(p_331912_));
                p_331057_.encode(p_332052_, p_331593_.apply(p_331912_));
                p_331117_.encode(p_332052_, p_331904_.apply(p_331912_));
            }
        };
    }

    static <B, T> StreamCodec<B, T> recursive(final UnaryOperator<StreamCodec<B, T>> p_330470_) {
        return new StreamCodec<B, T>() {
            private final Supplier<StreamCodec<B, T>> inner = Suppliers.memoize(() -> p_330470_.apply(this));

            @Override
            public T decode(B p_330903_) {
                return this.inner.get().decode(p_330903_);
            }

            @Override
            public void encode(B p_331641_, T p_330634_) {
                this.inner.get().encode(p_331641_, p_330634_);
            }
        };
    }

    default <S extends B> StreamCodec<S, V> cast() {
        return (StreamCodec<S, V>)this;
    }

    @FunctionalInterface
    public interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> p_320347_);
    }
}
