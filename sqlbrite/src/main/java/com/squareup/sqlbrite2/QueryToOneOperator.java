/*
 * Copyright (C) 2017 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.sqlbrite2;

import android.database.Cursor;
import io.reactivex.ObservableOperator;
import io.reactivex.Observer;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.plugins.RxJavaPlugins;

final class QueryToOneOperator<T> implements ObservableOperator<T, SqlBrite.Query> {
  private final Function<Cursor, T> mapper;
  private final boolean emitDefault;
  private final T defaultValue;

  QueryToOneOperator(Function<Cursor, T> mapper, boolean emitDefault, T defaultValue) {
    this.mapper = mapper;
    this.emitDefault = emitDefault;
    this.defaultValue = defaultValue;
  }

  @Override public Observer<? super SqlBrite.Query> apply(Observer<? super T> observer) {
    return new MappingObserver<>(observer, mapper, emitDefault, defaultValue);
  }

  static final class MappingObserver<T> extends DisposableObserver<SqlBrite.Query> {
    private final Observer<? super T> downstream;
    private final Function<Cursor, T> mapper;
    private final boolean emitDefault;
    private final T defaultValue;

    MappingObserver(Observer<? super T> downstream, Function<Cursor, T> mapper, boolean emitDefault,
        T defaultValue) {
      this.downstream = downstream;
      this.mapper = mapper;
      this.emitDefault = emitDefault;
      this.defaultValue = defaultValue;
    }

    @Override protected void onStart() {
      downstream.onSubscribe(this);
    }

    @Override public void onNext(SqlBrite.Query query) {
      try {
        boolean emit = false;
        T item = null;
        Cursor cursor = query.run();
        if (cursor != null) {
          try {
            if (cursor.moveToNext()) {
              item = mapper.apply(cursor);
              emit = true;
              if (cursor.moveToNext()) {
                throw new IllegalStateException("Cursor returned more than 1 row");
              }
            }
          } finally {
            cursor.close();
          }
        }
        if (!isDisposed()) {
          if (emit) {
            downstream.onNext(item);
          } else if (emitDefault) {
            downstream.onNext(defaultValue);
          }
        }
      } catch (Throwable e) {
        Exceptions.throwIfFatal(e);
        onError(e);
      }
    }

    @Override public void onComplete() {
      if (!isDisposed()) {
        downstream.onComplete();
      }
    }

    @Override public void onError(Throwable e) {
      if (isDisposed()) {
        RxJavaPlugins.onError(e);
      } else {
        downstream.onError(e);
      }
    }
  }
}
