import classNames from "classnames";
import React, { ReactNode, useCallback, useImperativeHandle, useRef } from "react";
import { useIntl } from "react-intl";
import { useToggle } from "react-use";

import styles from "./Input.module.scss";
import { Button } from "../Button";

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: boolean;
  light?: boolean;
  inline?: boolean;
  containerClassName?: string;
  adornment?: ReactNode;
  "data-testid"?: string;
  icon?: ReactNode;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ light, error, icon, inline, containerClassName, adornment, "data-testid": testId, ...props }, ref) => {
    const { formatMessage } = useIntl();

    const inputRef = useRef<HTMLInputElement | null>(null);
    const inputSelectionStartRef = useRef<number | null>(null);

    // Necessary to bind a ref passed from the parent in to our internal inputRef
    useImperativeHandle(ref, () => inputRef.current as HTMLInputElement, []);

    const [isContentVisible, toggleIsContentVisible] = useToggle(false);

    const isPassword = props.type === "password";
    const isVisibilityButtonVisible = isPassword && !props.disabled;
    const type = isPassword ? (isContentVisible ? "text" : "password") : props.type;

    const focusOnInputElement = useCallback(() => {
      if (!inputRef) {
        return;
      }

      const element = inputRef.current;
      const selectionStart = inputSelectionStartRef.current ?? inputRef.current?.value.length;

      element?.focus();
      if (selectionStart) {
        // In Chromium, .focus() is not synchronous. Requesting the next frame fixes this.
        requestAnimationFrame(() => {
          element?.setSelectionRange(selectionStart, selectionStart);
        });
      }
    }, [inputRef]);

    const onContainerBlur: React.FocusEventHandler<HTMLDivElement> = (event) => {
      // event.relatedTarget holds the new element that is gaining focus (or null, if focus is being lost by e.g.
      // clicking on a non-focusable element). This check tells us if the user clicked on the visibility button, in
      // which case we want to toggle visibliity but retain the cursor position.
      if (!event.currentTarget.contains(event.relatedTarget)) {
        inputSelectionStartRef.current = null;
        if (isVisibilityButtonVisible) {
          toggleIsContentVisible(false);
        }
      } else {
        inputSelectionStartRef.current = inputRef.current?.selectionStart ?? null;
      }
    };

    return (
      <div
        className={classNames(containerClassName, styles.container, {
          [styles.disabled]: props.disabled,
          [styles.readOnly]: props.readOnly,
          [styles.light]: light,
          [styles.inline]: inline,
          [styles.error]: error,
        })}
        data-testid="input-container"
        onBlur={onContainerBlur}
      >
        {icon && <span className={styles.icon}>{icon}</span>}
        <input
          aria-invalid={error}
          data-testid={testId ?? "input"}
          {...props}
          ref={inputRef}
          type={type}
          className={classNames(
            styles.input,
            {
              [styles.disabled]: props.disabled,
              [styles.readOnly]: props.readOnly,
              [styles.password]: isPassword,
              "fs-exclude": isPassword,
              [styles["input--hasIcon"]]: !!icon,
            },
            props.className
          )}
        />
        {adornment}
        {isVisibilityButtonVisible ? (
          <Button
            className={styles.visibilityButton}
            onClick={() => {
              toggleIsContentVisible();
              focusOnInputElement();
            }}
            tabIndex={-1}
            type="button"
            variant="clear"
            aria-label={formatMessage({
              id: `ui.input.${isContentVisible ? "hide" : "show"}Password`,
            })}
            data-testid="toggle-password-visibility-button"
            icon={isContentVisible ? "eyeSlash" : "eye"}
          />
        ) : null}
      </div>
    );
  }
);
Input.displayName = "Input";
