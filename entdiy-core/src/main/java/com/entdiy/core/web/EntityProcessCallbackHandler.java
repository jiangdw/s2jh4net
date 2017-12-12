/**
 * Copyright © 2015 - 2017 EntDIY JavaEE Development Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.entdiy.core.web;

/**
 * 用于批量数据单一实体处理逻辑匿名回调接口
 * @param <T>
 */
public interface EntityProcessCallbackHandler<T> {

    void processEntity(T entity) throws EntityProcessCallbackException;

    public class EntityProcessCallbackException extends Exception {

        private static final long serialVersionUID = -2803641078892909145L;

        public EntityProcessCallbackException(String msg) {
            super(msg);
        }
    }
}