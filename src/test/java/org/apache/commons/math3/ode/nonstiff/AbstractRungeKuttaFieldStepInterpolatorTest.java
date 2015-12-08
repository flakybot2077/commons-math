/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.math3.ode.nonstiff;


import java.lang.reflect.InvocationTargetException;

import org.apache.commons.math3.Field;
import org.apache.commons.math3.RealFieldElement;
import org.apache.commons.math3.ode.EquationsMapper;
import org.apache.commons.math3.ode.FieldEquationsMapper;
import org.apache.commons.math3.ode.FieldExpandableODE;
import org.apache.commons.math3.ode.FieldFirstOrderDifferentialEquations;
import org.apache.commons.math3.ode.FieldODEStateAndDerivative;
import org.apache.commons.math3.ode.sampling.AbstractFieldStepInterpolator;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathArrays;
import org.junit.Assert;
import org.junit.Test;

public abstract class AbstractRungeKuttaFieldStepInterpolatorTest {

    protected abstract <T extends RealFieldElement<T>> RungeKuttaFieldStepInterpolator<T>
        createInterpolator(Field<T> field, boolean forward, FieldEquationsMapper<T> mapper);

    @Test
    public abstract void interpolationAtBounds();

    protected <T extends RealFieldElement<T>> void doInterpolationAtBounds(final Field<T> field, double epsilon) {

        RungeKuttaFieldStepInterpolator<T> interpolator = setUpInterpolator(field,
                                                                            new SinCos<>(field),
                                                                            0.0, new double[] { 0.0, 1.0 }, 0.125);

        Assert.assertEquals(0.0, interpolator.getPreviousState().getTime().getReal(), 1.0e-15);
        for (int i = 0; i < 2; ++i) {
            Assert.assertEquals(interpolator.getPreviousState().getState()[i].getReal(),
                                interpolator.getInterpolatedState(interpolator.getPreviousState().getTime()).getState()[i].getReal(),
                                epsilon);
        }
        Assert.assertEquals(0.125, interpolator.getCurrentState().getTime().getReal(), 1.0e-15);
        for (int i = 0; i < 2; ++i) {
            Assert.assertEquals(interpolator.getCurrentState().getState()[i].getReal(),
                                interpolator.getInterpolatedState(interpolator.getCurrentState().getTime()).getState()[i].getReal(),
                                epsilon);
        }

    }

    @Test
    public abstract void interpolationInside();

    protected <T extends RealFieldElement<T>> void doInterpolationInside(final Field<T> field,
                                                                         double epsilonSin, double epsilonCos) {

        RungeKuttaFieldStepInterpolator<T> interpolator = setUpInterpolator(field,
                                                                            new SinCos<>(field),
                                                                            0.0, new double[] { 0.0, 1.0 }, 0.125);

        int n = 100;
        double maxErrorSin = 0;
        double maxErrorCos = 0;
        for (int i = 0; i <= n; ++i) {
            T t =     interpolator.getPreviousState().getTime().multiply(n - i).
                  add(interpolator.getCurrentState().getTime().multiply(i)).
                  divide(n);
            FieldODEStateAndDerivative<T> state = interpolator.getInterpolatedState(t);
            maxErrorSin = FastMath.max(maxErrorSin, state.getState()[0].subtract(t.sin()).abs().getReal());
            maxErrorCos = FastMath.max(maxErrorCos, state.getState()[1].subtract(t.cos()).abs().getReal());
        }
        Assert.assertEquals(0.0, maxErrorSin, epsilonSin);
        Assert.assertEquals(0.0, maxErrorCos, epsilonCos);

    }

    @Test
    public abstract void nonFieldInterpolatorConsistency();

    protected <T extends RealFieldElement<T>> void doNonFieldInterpolatorConsistency(final Field<T> field,
                                                                                     double epsilonSin, double epsilonCos,
                                                                                     double epsilonSinDot, double epsilonCosDot) {

        RungeKuttaFieldStepInterpolator<T> fieldInterpolator =
                        setUpInterpolator(field, new SinCos<>(field), 0.0, new double[] { 0.0, 1.0 }, 0.125);
        RungeKuttaStepInterpolator regularInterpolator = convertInterpolator(fieldInterpolator);

        int n = 100;
        double maxErrorSin    = 0;
        double maxErrorCos    = 0;
        double maxErrorSinDot = 0;
        double maxErrorCosDot = 0;
        for (int i = 0; i <= n; ++i) {

            T t =     fieldInterpolator.getPreviousState().getTime().multiply(n - i).
                  add(fieldInterpolator.getCurrentState().getTime().multiply(i)).
                  divide(n);

            FieldODEStateAndDerivative<T> state = fieldInterpolator.getInterpolatedState(t);
            T[] fieldY    = state.getState();
            T[] fieldYDot = state.getDerivative();

            regularInterpolator.setInterpolatedTime(t.getReal());
            double[] regularY     = regularInterpolator.getInterpolatedState();
            double[] regularYDot  = regularInterpolator.getInterpolatedDerivatives();

            maxErrorSin    = FastMath.max(maxErrorSin,    fieldY[0].subtract(regularY[0]).abs().getReal());
            maxErrorCos    = FastMath.max(maxErrorCos,    fieldY[1].subtract(regularY[1]).abs().getReal());
            maxErrorSinDot = FastMath.max(maxErrorSinDot, fieldYDot[0].subtract(regularYDot[0]).abs().getReal());
            maxErrorCosDot = FastMath.max(maxErrorCosDot, fieldYDot[1].subtract(regularYDot[1]).abs().getReal());

        }
        Assert.assertEquals(0.0, maxErrorSin,    epsilonSin);
        Assert.assertEquals(0.0, maxErrorCos,    epsilonCos);
        Assert.assertEquals(0.0, maxErrorSinDot, epsilonSinDot);
        Assert.assertEquals(0.0, maxErrorCosDot, epsilonCosDot);

    }

    private <T extends RealFieldElement<T>>
    RungeKuttaFieldStepInterpolator<T> setUpInterpolator(final Field<T> field,
                                                         final FieldFirstOrderDifferentialEquations<T> eqn,
                                                         final double t0, final double[] y0,
                                                         final double t1) {

        RungeKuttaFieldStepInterpolator<T> interpolator = createInterpolator(field, t1 > t0,
                                                                             new FieldExpandableODE<T>(eqn).getMapper());
        // get the Butcher arrays from the field integrator
        FieldButcherArrayProvider<T> provider = createButcherArrayProvider(field, interpolator);
        T[][] a = provider.getA();
        T[]   b = provider.getB();
        T[]   c = provider.getC();

        // store initial state
        T     t          = field.getZero().add(t0);
        T[]   fieldY     = MathArrays.buildArray(field, eqn.getDimension());
        T[][] fieldYDotK = MathArrays.buildArray(field, b.length, -1);
        for (int i = 0; i < y0.length; ++i) {
            fieldY[i] = field.getZero().add(y0[i]);
        }
        fieldYDotK[0] = eqn.computeDerivatives(t, fieldY);
        interpolator.storeState(new FieldODEStateAndDerivative<T>(t, fieldY, fieldYDotK[0]));
        interpolator.shift();

        // perform one integration step, in order to get consistent derivatives
        T h = field.getZero().add(t1 - t0);
        for (int k = 0; k < a.length; ++k) {
            for (int i = 0; i < y0.length; ++i) {
                fieldY[i] = field.getZero().add(y0[i]);
                for (int s = 0; s <= k; ++s) {
                    fieldY[i] = fieldY[i].add(h.multiply(a[k][s].multiply(fieldYDotK[s][i])));
                }
            }
            fieldYDotK[k + 1] = eqn.computeDerivatives(h.multiply(c[k]).add(t0), fieldY);
        }
        interpolator.setSlopes(fieldYDotK);

        // store state at step end
        for (int i = 0; i < y0.length; ++i) {
            fieldY[i] = field.getZero().add(y0[i]);
            for (int s = 0; s < b.length; ++s) {
                fieldY[i] = fieldY[i].add(h.multiply(b[s].multiply(fieldYDotK[s][i])));
            }
        }
        interpolator.storeState(new FieldODEStateAndDerivative<T>(field.getZero().add(t1),
                                                                  fieldY,
                                                                  eqn.computeDerivatives(field.getZero().add(t1), fieldY)));

        return interpolator;

    }

    private <T extends RealFieldElement<T>>
    RungeKuttaStepInterpolator convertInterpolator(final RungeKuttaFieldStepInterpolator<T> fieldInterpolator) {

        RungeKuttaStepInterpolator regularInterpolator = null;
        try {

            String interpolatorName = fieldInterpolator.getClass().getName();
            String integratorName = interpolatorName.replaceAll("Field", "");
            @SuppressWarnings("unchecked")
            Class<RungeKuttaStepInterpolator> clz = (Class<RungeKuttaStepInterpolator>) Class.forName(integratorName);
            regularInterpolator = clz.newInstance();

            double[][] yDotArray = null;
            java.lang.reflect.Field fYD = RungeKuttaFieldStepInterpolator.class.getDeclaredField("yDotK");
            fYD.setAccessible(true);
            @SuppressWarnings("unchecked")
            T[][] fieldYDotk = (T[][]) fYD.get(fieldInterpolator);
            yDotArray = new double[fieldYDotk.length][];
            for (int i = 0; i < yDotArray.length; ++i) {
                yDotArray[i] = new double[fieldYDotk[i].length];
                for (int j = 0; j < yDotArray[i].length; ++j) {
                    yDotArray[i][j] = fieldYDotk[i][j].getReal();
                }
            }
            double[] y = new double[yDotArray[0].length];

            EquationsMapper primaryMapper = null;
            EquationsMapper[] secondaryMappers = null;
            java.lang.reflect.Field fMapper = AbstractFieldStepInterpolator.class.getDeclaredField("mapper");
            fMapper.setAccessible(true);
            @SuppressWarnings("unchecked")
            FieldEquationsMapper<T> mapper = (FieldEquationsMapper<T>) fMapper.get(fieldInterpolator);
            java.lang.reflect.Field fStart = FieldEquationsMapper.class.getDeclaredField("start");
            fStart.setAccessible(true);
            int[] start = (int[]) fStart.get(mapper);
            primaryMapper = new EquationsMapper(start[0], start[1]);
            secondaryMappers = new EquationsMapper[mapper.getNumberOfEquations() - 1];
            for (int i = 0; i < secondaryMappers.length; ++i) {
                secondaryMappers[i] = new EquationsMapper(start[i + 1], start[i + 2]);
            }

            regularInterpolator.reinitialize(null, y, yDotArray,
                                             fieldInterpolator.isForward(),
                                             primaryMapper, secondaryMappers);

            T[] fieldPreviousY = fieldInterpolator.getPreviousState().getState();
            for (int i = 0; i < y.length; ++i) {
                y[i] = fieldPreviousY[i].getReal();
            }
            regularInterpolator.storeTime(fieldInterpolator.getPreviousState().getTime().getReal());

            regularInterpolator.shift();

            T[] fieldCurrentY = fieldInterpolator.getCurrentState().getState();
            for (int i = 0; i < y.length; ++i) {
                y[i] = fieldCurrentY[i].getReal();
            }
            regularInterpolator.storeTime(fieldInterpolator.getCurrentState().getTime().getReal());

        } catch (ClassNotFoundException cnfe) {
            Assert.fail(cnfe.getLocalizedMessage());
        } catch (InstantiationException ie) {
            Assert.fail(ie.getLocalizedMessage());
        } catch (IllegalAccessException iae) {
            Assert.fail(iae.getLocalizedMessage());
        } catch (NoSuchFieldException nsfe) {
            Assert.fail(nsfe.getLocalizedMessage());
        } catch (IllegalArgumentException iae) {
            Assert.fail(iae.getLocalizedMessage());
        }

        return regularInterpolator;

    }

    private <T extends RealFieldElement<T>> FieldButcherArrayProvider<T>
    createButcherArrayProvider(final Field<T> field, final RungeKuttaFieldStepInterpolator<T> provider) {
        FieldButcherArrayProvider<T> integrator = null;
        try {
        String interpolatorName = provider.getClass().getName();
        String integratorName = interpolatorName.replaceAll("StepInterpolator", "Integrator");
            @SuppressWarnings("unchecked")
            Class<FieldButcherArrayProvider<T>> clz = (Class<FieldButcherArrayProvider<T>>) Class.forName(integratorName);
            try {
                integrator = clz.getConstructor(Field.class, RealFieldElement.class).
                                                newInstance(field, field.getOne());
            } catch (NoSuchMethodException nsme) {
                try {
                    integrator = clz.getConstructor(Field.class,
                                                    Double.TYPE, Double.TYPE,
                                                    Double.TYPE, Double.TYPE).
                                 newInstance(field, 0.001, 1.0, 1.0, 1.0);
                } catch (NoSuchMethodException e) {
                    Assert.fail(e.getLocalizedMessage());
                }
            }

        } catch (InvocationTargetException ite) {
            Assert.fail(ite.getLocalizedMessage());
        } catch (IllegalAccessException iae) {
            Assert.fail(iae.getLocalizedMessage());
        } catch (InstantiationException ie) {
            Assert.fail(ie.getLocalizedMessage());
        } catch (ClassNotFoundException cnfe) {
            Assert.fail(cnfe.getLocalizedMessage());
        }

        return integrator;

    }

    private static class SinCos<T extends RealFieldElement<T>> implements FieldFirstOrderDifferentialEquations<T> {
        private final Field<T> field;
        protected SinCos(final Field<T> field) {
            this.field = field;
        }
        public int getDimension() {
            return 2;
        }
        public void init(final T t0, final T[] y0, final T finalTime) {
        }
        public T[] computeDerivatives(final T t, final T[] y) {
            T[] yDot = MathArrays.buildArray(field, 2);
            yDot[0] = y[1];
            yDot[1] = y[0].negate();
            return yDot;
        }
    }

}