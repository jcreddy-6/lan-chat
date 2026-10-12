package com.lanchat.client.ui;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/** Text field with rounded corners, a focus outline and an optional hint text. */
public class RoundedTextField extends JTextField {

    private String placeholder = "";

    public RoundedTextField(int columns) {
        super(columns);
        setOpaque(false);
        setFont(Theme.font(Font.PLAIN, 14f));
        setForeground(Theme.TEXT);
        setCaretColor(Theme.PRIMARY);
        setBorder(BorderFactory.createEmptyBorder(11, 14, 11, 14));
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    public void setPlaceholder(String text) {
        this.placeholder = text;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        FieldPainter.background(g, this);
        super.paintComponent(g);
        FieldPainter.decorate(g, this, placeholder, getText().isEmpty());
    }
}
